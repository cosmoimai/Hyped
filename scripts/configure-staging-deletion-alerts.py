#!/usr/bin/env python3
"""Render or apply aggregate-only deletion alerts in Hyped's staging project."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request

PROJECT = 'hyped-41a30'
METRIC = 'hyped_staging_account_deletion_heartbeat'
OWNER = {'managed_by': 'hyped-staging-script', 'hyped_component': 'account-deletion'}
RUNTIME = 'resource.type="cloud_run_revision" AND resource.labels.service_name="hyped-staging-api"'
HEALTH = 'textPayload:"code=ACCOUNT_DELETION_HEALTH"'
RUNBOOK = 'See docs/12-observability-runbook.md, account-deletion monitoring. '


def plan(channel):
    metric = {
        'name': METRIC,
        'description': 'Successful aggregate account-deletion backlog observations; no user labels.',
        'filter': RUNTIME + ' AND ' + HEALTH + ' AND NOT textPayload:"status=UNAVAILABLE"',
        'metricDescriptor': {'metricKind': 'DELTA', 'valueType': 'INT64', 'unit': '1'},
    }

    def log_policy(title, query, explanation):
        return {
            'displayName': title, 'enabled': True, 'combiner': 'OR', 'userLabels': OWNER,
            'notificationChannels': [channel],
            'documentation': {'mimeType': 'text/markdown', 'content': RUNBOOK + explanation},
            'conditions': [{'displayName': title, 'conditionMatchedLog': {'filter': query}}],
            'alertStrategy': {'notificationRateLimit': {'period': '3600s'}, 'autoClose': '1800s'},
        }

    policies = [
        log_policy('Hyped staging: deletion cleanup needs attention',
                   RUNTIME + ' AND ' + HEALTH +
                   ' AND textPayload =~ "status=(BACKLOG|OVERDUE|UNAVAILABLE)"',
                   'BACKLOG starts at 12 elapsed hours; OVERDUE at 24 hours. UNAVAILABLE means the '
                   'backlog cannot be read. Restore dependencies and use the authenticated worker. '
                   'Never reset acceptance time or restore a deletion-pending account.'),
        log_policy('Hyped staging: cleanup scheduler failed',
                   'resource.type="cloud_scheduler_job" AND '
                   'resource.labels.job_id="hyped-staging-reminders" AND severity>=ERROR',
                   'Inspect scheduler OIDC identity/audience, Cloud Run health and dependency availability. '
                   'This policy alerts on a failed attempt; it does not prove individual purges completed.'),
        {
            'displayName': 'Hyped staging: deletion cleanup heartbeat missing',
            'enabled': True, 'combiner': 'OR', 'userLabels': OWNER,
            'notificationChannels': [channel],
            'documentation': {'mimeType': 'text/markdown', 'content': RUNBOOK +
                              'No successful backlog observation for 10 minutes. Check paused/missing '
                              'scheduler, worker failures, logging and metric ingestion. '
                              'A new metric needs an observed time series before absence can be detected.'},
            'conditions': [{
                'displayName': 'No aggregate cleanup heartbeat for 10 minutes',
                'conditionAbsent': {
                    'filter': 'metric.type="logging.googleapis.com/user/' + METRIC + '" AND ' + RUNTIME,
                    'duration': '600s', 'trigger': {'count': 1},
                    'aggregations': [{'alignmentPeriod': '60s', 'perSeriesAligner': 'ALIGN_SUM',
                                      'crossSeriesReducer': 'REDUCE_SUM'}],
                },
            }],
            'alertStrategy': {'autoClose': '1800s'},
        },
    ]
    return metric, policies


class Api:
    def __init__(self):
        result = subprocess.run(['gcloud', '--configuration=hyped-staging', 'auth', 'print-access-token'],
                                capture_output=True, text=True, check=False)
        if result.returncode:
            raise RuntimeError('Sign in to the existing hyped-staging gcloud configuration')
        self.token = result.stdout.strip()

    def request(self, host, path, method='GET', payload=None, allow_missing=False):
        request = urllib.request.Request('https://' + host + path, method=method,
                                        data=None if payload is None else json.dumps(payload).encode(),
                                        headers={'Authorization': 'Bearer ' + self.token,
                                                 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if allow_missing and error.code == 404:
                return None
            raise RuntimeError('Monitoring configuration request failed: HTTP ' + str(error.code)) from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--channel-file', type=Path, required=True,
                        help='File containing the existing notification-channel resource name, not an email')
    parser.add_argument('--output', type=Path, default=Path('build/verification/deletion-monitoring/alert-plan.json'))
    parser.add_argument('--apply', action='store_true', help='Create/update only the named staging metric and policies')
    args = parser.parse_args()
    channel = args.channel_file.read_text().strip()
    if not re.fullmatch(r'projects/hyped-41a30/notificationChannels/[0-9]+', channel):
        parser.error('Expected a notification-channel resource in the staging project')
    metric, policies = plan(channel)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({'metric': metric, 'policies': policies}, indent=2) + '\n')
    if not args.apply:
        print('Rendered staging alert plan; no cloud resources changed')
        return
    api = Api()
    monitoring = '/v3/projects/' + PROJECT + '/'
    existing_channel = api.request('monitoring.googleapis.com', '/v3/' + channel)
    if existing_channel.get('type') != 'email' or not existing_channel.get('enabled', True):
        raise RuntimeError('The selected operations channel must be enabled and use email')
    response = api.request('monitoring.googleapis.com', monitoring + 'alertPolicies')
    existing = response.get('alertPolicies', [])
    while response.get('nextPageToken'):
        response = api.request('monitoring.googleapis.com', monitoring + 'alertPolicies?pageToken=' +
                               urllib.parse.quote(response['nextPageToken'], safe=''))
        existing.extend(response.get('alertPolicies', []))
    for policy in policies:
        matches = [item for item in existing if item.get('displayName') == policy['displayName']]
        if len(matches) > 1:
            raise RuntimeError('Duplicate staging policy names require manual review')
        if matches and any(matches[0].get('userLabels', {}).get(key) != value for key, value in OWNER.items()):
            raise RuntimeError('Refusing to replace a policy not owned by this staging script')
    logging = '/v2/projects/' + PROJECT + '/metrics'
    current = api.request('logging.googleapis.com', logging + '/' + METRIC, allow_missing=True)
    api.request('logging.googleapis.com', logging + ('/' + METRIC if current else ''),
                'PUT' if current else 'POST', metric)
    for policy in policies:
        matches = [item for item in existing if item.get('displayName') == policy['displayName']]
        if matches:
            previous = matches[0]
            policy['name'] = previous['name']
            api.request('monitoring.googleapis.com', '/v3/' + previous['name'] +
                        '?updateMask=displayName,documentation,conditions,combiner,enabled,notificationChannels,alertStrategy,userLabels',
                        'PATCH', policy)
        else:
            api.request('monitoring.googleapis.com', monitoring + 'alertPolicies', 'POST', policy)
        print('Configured:', policy['displayName'])


if __name__ == '__main__':
    main()
