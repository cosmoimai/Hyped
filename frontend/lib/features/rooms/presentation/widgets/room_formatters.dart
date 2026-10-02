String compactDate(DateTime value) {
  final local = value.toLocal();
  return '${_month(local.month)} ${local.day}, ${local.year}';
}

String compactDateTime(DateTime value) {
  final local = value.toLocal();
  final hour = local.hour % 12 == 0 ? 12 : local.hour % 12;
  final minute = local.minute.toString().padLeft(2, '0');
  final suffix = local.hour >= 12 ? 'PM' : 'AM';
  return '${compactDate(value)} at $hour:$minute $suffix';
}

String countdown(DateTime eventAt, DateTime now) {
  final remaining = eventAt.difference(now.toUtc());
  if (remaining <= Duration.zero) return 'Event started';
  final days = remaining.inDays;
  final hours = remaining.inHours.remainder(24);
  final minutes = remaining.inMinutes.remainder(60);
  if (days > 0) return '${days}d ${hours}h';
  if (hours > 0) return '${hours}h ${minutes}m';
  if (minutes > 0) return '${minutes}m';
  return 'Less than 1m';
}

String _month(int month) => const [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
][month - 1];
