import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class RoomCodeEntryScreen extends StatefulWidget {
  const RoomCodeEntryScreen({super.key});

  @override
  State<RoomCodeEntryScreen> createState() => _RoomCodeEntryScreenState();
}

class _RoomCodeEntryScreenState extends State<RoomCodeEntryScreen> {
  final _controller = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _continue() {
    final code = _canonicalCode(_controller.text);
    if (!RegExp(r'^[A-HJ-NP-Z2-9]{8}$').hasMatch(code)) {
      setState(() => _error = 'Enter an 8-character room code.');
      return;
    }
    context.go('/join/room-code/${Uri.encodeComponent(code)}');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Join a room')),
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text(
            'Enter room code',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          const Text('Ask the room host for the 8-character Hyped! code.'),
          const SizedBox(height: 24),
          TextField(
            controller: _controller,
            textCapitalization: TextCapitalization.characters,
            autocorrect: false,
            enableSuggestions: false,
            maxLength: 9,
            decoration: InputDecoration(
              labelText: 'Room code',
              hintText: '7K4M-P9QX',
              errorText: _error,
              border: const OutlineInputBorder(),
            ),
            onSubmitted: (_) => _continue(),
          ),
          const SizedBox(height: 12),
          FilledButton(onPressed: _continue, child: const Text('Preview room')),
        ],
      ),
    ),
  );
}

String _canonicalCode(String value) =>
    value.replaceAll('-', '').trim().toUpperCase();
