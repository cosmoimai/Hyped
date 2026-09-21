import 'package:flutter/material.dart';

class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.auto_awesome, size: 64, color: Color(0xFF6C8CFF)),
          const SizedBox(height: 20),
          const Text(
            'Hyped!',
            style: TextStyle(fontSize: 32, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 24),
          Semantics(
            label: 'Loading Hyped',
            child: const CircularProgressIndicator(),
          ),
        ],
      ),
    ),
  );
}
