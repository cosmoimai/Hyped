import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deletion_alerts', Path(__file__).parents[1] / 'configure-staging-deletion-alerts.py')
alerts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(alerts)
CHANNEL = 'projects/hyped-41a30/notificationChannels/123'


class DeletionAlertSafetyTest(unittest.TestCase):
    def test_preview_never_authenticates_or_modifies_cloud_resources(self):
        with tempfile.TemporaryDirectory() as folder:
            channel = Path(folder) / 'channel'
            output = Path(folder) / 'plan.json'
            channel.write_text(CHANNEL)
            with patch.object(alerts, 'Api') as api, patch('sys.argv', [
                    'alerts', '--channel-file', str(channel), '--output', str(output)]):
                alerts.main()
            api.assert_not_called()
            plan = json.loads(output.read_text())
            self.assertEqual(len(plan['policies']), 3)
            self.assertNotIn('email', json.dumps(plan))

    def test_foreign_project_cannot_be_targeted(self):
        with tempfile.TemporaryDirectory() as folder:
            channel = Path(folder) / 'channel'
            channel.write_text('projects/production/notificationChannels/123')
            with patch.object(alerts, 'Api') as api, patch('sys.argv', ['alerts', '--channel-file', str(channel)]):
                with self.assertRaises(SystemExit):
                    alerts.main()
            api.assert_not_called()

    def test_heartbeat_combines_revisions_instead_of_alerting_on_retired_instances(self):
        metric, policies = alerts.plan(CHANNEL)
        condition = policies[2]['conditions'][0]['conditionAbsent']
        self.assertNotIn('revision_name', condition['filter'])
        self.assertEqual(condition['aggregations'][0]['crossSeriesReducer'], 'REDUCE_SUM')
        self.assertEqual(condition['duration'], '600s')
        self.assertIn('NOT textPayload:"status=UNAVAILABLE"', metric['filter'])

    def test_existing_unowned_policy_is_not_overwritten(self):
        metric, policies = alerts.plan(CHANNEL)
        class FakeApi:
            mutations = []
            def request(self, host, path, method='GET', payload=None, **kwargs):
                if method != 'GET':
                    self.mutations.append((path, method))
                if path.endswith('/' + CHANNEL.split('/')[-1]):
                    return {'type': 'email', 'enabled': True}
                if path.endswith('/alertPolicies'):
                    return {'alertPolicies': [{'name': 'projects/hyped-41a30/alertPolicies/456',
                                              'displayName': policies[0]['displayName']}]}
                return {}
        with tempfile.TemporaryDirectory() as folder:
            channel = Path(folder) / 'channel'
            output = Path(folder) / 'plan.json'
            channel.write_text(CHANNEL)
            with patch.object(alerts, 'Api', FakeApi), patch('sys.argv', [
                    'alerts', '--channel-file', str(channel), '--output', str(output), '--apply']):
                with self.assertRaisesRegex(RuntimeError, 'not owned'):
                    alerts.main()
            self.assertEqual(FakeApi.mutations, [])


if __name__ == '__main__':
    unittest.main()
