import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hyped/features/policy/data/policy_api.dart';
import 'package:hyped/features/policy/presentation/policy_providers.dart';

class LegalDocumentScreen extends ConsumerStatefulWidget {
  const LegalDocumentScreen({required this.kind, this.version, super.key});
  final String kind;
  final String? version;
  @override
  ConsumerState<LegalDocumentScreen> createState() =>
      _LegalDocumentScreenState();
}

class _LegalDocumentScreenState extends ConsumerState<LegalDocumentScreen> {
  late Future<LegalDocument> _document;
  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    _document = ref
        .read(policyApiProvider)
        .document(widget.kind, widget.version);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(
        widget.kind == 'terms' ? 'Terms of Service' : 'Privacy Policy',
      ),
    ),
    body: FutureBuilder<LegalDocument>(
      future: _document,
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Could not load this document. Please retry.'),
                  TextButton(
                    onPressed: () => setState(_load),
                    child: const Text('Retry'),
                  ),
                ],
              ),
            ),
          );
        }
        final document = snapshot.data;
        if (document == null) {
          return const Center(child: CircularProgressIndicator());
        }
        return SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: SelectionArea(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (!document.published)
                  const Text(
                    'Draft — not yet published',
                    style: TextStyle(fontWeight: FontWeight.bold),
                  ),
                Text(
                  document.title,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                Text('Version ${document.version}'),
                const SizedBox(height: 20),
                Text(document.content),
              ],
            ),
          ),
        );
      },
    ),
  );
}
