import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/domain/room_commands.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_formatters.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';

class CreateRoomScreen extends ConsumerStatefulWidget {
  const CreateRoomScreen({super.key});

  @override
  ConsumerState<CreateRoomScreen> createState() => _CreateRoomScreenState();
}

class _CreateRoomScreenState extends ConsumerState<CreateRoomScreen> {
  late final RoomFormModel model;
  int step = 0;

  @override
  void initState() {
    super.initState();
    model = RoomFormModel.initial();
  }

  @override
  void dispose() {
    model.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(roomCreationControllerProvider);
    return RoomFormScaffold(
      title: 'Create room',
      step: step,
      model: model,
      error: controller.error,
      saving: controller.submitting,
      onStepChanged: (value) => setState(() => step = value),
      onSubmit: () async {
        final room = await ref
            .read(roomCreationControllerProvider)
            .submit(model.draft);
        if (room == null || !context.mounted) return;
        context.go('/rooms/${room.id}');
      },
    );
  }
}

class EditRoomScreen extends ConsumerStatefulWidget {
  const EditRoomScreen({required this.roomId, super.key});

  final String roomId;

  @override
  ConsumerState<EditRoomScreen> createState() => _EditRoomScreenState();
}

class _EditRoomScreenState extends ConsumerState<EditRoomScreen> {
  RoomFormModel? model;
  int step = 0;

  @override
  void dispose() {
    model?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = ref.watch(roomDetailControllerProvider(widget.roomId));
    final room = controller.room;
    if (controller.loading && room == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (room == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Edit room')),
        body: Center(child: Text(controller.error ?? 'Room unavailable.')),
      );
    }
    if (!room.canEdit) {
      return Scaffold(
        appBar: AppBar(title: const Text('Edit room')),
        body: const Center(child: Text('Only hosts can edit this room.')),
      );
    }
    model ??= RoomFormModel.fromRoom(room);
    return RoomFormScaffold(
      title: 'Edit room',
      step: step,
      model: model!,
      error: controller.error,
      saving: controller.saving,
      onStepChanged: (value) => setState(() => step = value),
      onSubmit: () async {
        final saved = await ref
            .read(roomDetailControllerProvider(widget.roomId))
            .save(model!.draft);
        if (!saved || !context.mounted) return;
        context.go('/rooms/${widget.roomId}');
      },
    );
  }
}

class RoomFormScaffold extends StatefulWidget {
  const RoomFormScaffold({
    required this.title,
    required this.step,
    required this.model,
    required this.error,
    required this.saving,
    required this.onStepChanged,
    required this.onSubmit,
    super.key,
  });

  final String title;
  final int step;
  final RoomFormModel model;
  final String? error;
  final bool saving;
  final ValueChanged<int> onStepChanged;
  final Future<void> Function() onSubmit;

  @override
  State<RoomFormScaffold> createState() => _RoomFormScaffoldState();
}

class _RoomFormScaffoldState extends State<RoomFormScaffold> {
  final formKey = GlobalKey<FormState>();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: SafeArea(
        child: Form(
          key: formKey,
          child: Stepper(
            currentStep: widget.step,
            onStepTapped: widget.onStepChanged,
            controlsBuilder: (context, details) {
              final last = widget.step == 2;
              return Padding(
                padding: const EdgeInsets.only(top: 20),
                child: Row(
                  children: [
                    Expanded(
                      child: FilledButton(
                        onPressed: widget.saving
                            ? null
                            : () async {
                                if (widget.step == 0 &&
                                    !(formKey.currentState?.validate() ??
                                        false)) {
                                  return;
                                }
                                if (last) {
                                  await widget.onSubmit();
                                } else {
                                  widget.onStepChanged(widget.step + 1);
                                }
                              },
                        child: widget.saving
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : Text(last ? 'Save room' : 'Continue'),
                      ),
                    ),
                    if (widget.step > 0) ...[
                      const SizedBox(width: 12),
                      TextButton(
                        onPressed: widget.saving
                            ? null
                            : () => widget.onStepChanged(widget.step - 1),
                        child: const Text('Back'),
                      ),
                    ],
                  ],
                ),
              );
            },
            steps: [
              Step(
                title: const Text('Details'),
                isActive: widget.step >= 0,
                content: _DetailsStep(model: widget.model),
              ),
              Step(
                title: const Text('Style'),
                isActive: widget.step >= 1,
                content: _StyleStep(model: widget.model),
              ),
              Step(
                title: const Text('Review'),
                isActive: widget.step >= 2,
                content: _ReviewStep(model: widget.model, error: widget.error),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DetailsStep extends StatefulWidget {
  const _DetailsStep({required this.model});

  final RoomFormModel model;

  @override
  State<_DetailsStep> createState() => _DetailsStepState();
}

class _DetailsStepState extends State<_DetailsStep> {
  @override
  Widget build(BuildContext context) {
    final model = widget.model;
    return Column(
      children: [
        TextFormField(
          controller: model.title,
          decoration: const InputDecoration(labelText: 'Title'),
          maxLength: 80,
          validator: (value) {
            final trimmed = value?.trim() ?? '';
            if (trimmed.isEmpty) return 'Add a title.';
            if (trimmed.length > 80) return 'Keep it under 80 characters.';
            return null;
          },
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () async {
                  final picked = await showDatePicker(
                    context: context,
                    firstDate: DateTime.now(),
                    lastDate: DateTime.now().add(const Duration(days: 3650)),
                    initialDate: model.date,
                  );
                  if (picked != null) setState(() => model.date = picked);
                },
                icon: const Icon(Icons.event),
                label: Text(compactDate(model.date)),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: () async {
                  final picked = await showTimePicker(
                    context: context,
                    initialTime: TimeOfDay(
                      hour: model.time.hour,
                      minute: model.time.minute,
                    ),
                  );
                  if (picked != null) {
                    setState(
                      () => model.time = (
                        hour: picked.hour,
                        minute: picked.minute,
                      ),
                    );
                  }
                },
                icon: const Icon(Icons.schedule),
                label: Text(
                  TimeOfDay(
                    hour: model.time.hour,
                    minute: model.time.minute,
                  ).format(context),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(
          initialValue: model.timeZone,
          decoration: const InputDecoration(labelText: 'Timezone'),
          items: supportedTimeZones
              .map((zone) => DropdownMenuItem(value: zone, child: Text(zone)))
              .toList(growable: false),
          onChanged: (value) => setState(() => model.timeZone = value ?? 'UTC'),
        ),
        TextFormField(
          controller: model.location,
          decoration: const InputDecoration(labelText: 'Location (optional)'),
          maxLength: 120,
        ),
        TextFormField(
          controller: model.description,
          decoration: const InputDecoration(
            labelText: 'Description (optional)',
          ),
          maxLength: 500,
          maxLines: 3,
        ),
      ],
    );
  }
}

class _StyleStep extends StatefulWidget {
  const _StyleStep({required this.model});

  final RoomFormModel model;

  @override
  State<_StyleStep> createState() => _StyleStepState();
}

class _StyleStepState extends State<_StyleStep> {
  @override
  Widget build(BuildContext context) {
    return GridView.count(
      crossAxisCount: MediaQuery.sizeOf(context).width > 520 ? 4 : 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      children: [
        for (final theme in roomThemeChoices)
          InkWell(
            onTap: () => setState(() => widget.model.theme = theme),
            borderRadius: BorderRadius.circular(24),
            child: Stack(
              children: [
                Positioned.fill(
                  child: RoomThemeView(
                    theme: theme,
                    child: const SizedBox.expand(),
                  ),
                ),
                if (theme.presetKey == widget.model.theme.presetKey)
                  const Positioned(
                    right: 12,
                    top: 12,
                    child: Icon(Icons.check_circle, color: Colors.white),
                  ),
              ],
            ),
          ),
      ],
    );
  }
}

class _ReviewStep extends StatelessWidget {
  const _ReviewStep({required this.model, required this.error});

  final RoomFormModel model;
  final String? error;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        RoomThemeView(
          theme: model.theme,
          child: DefaultTextStyle(
            style: Theme.of(
              context,
            ).textTheme.bodyLarge!.copyWith(color: Colors.white),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  model.title.text.trim().isEmpty
                      ? 'Untitled room'
                      : model.title.text.trim(),
                  style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    color: Colors.white,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 8),
                Text('${compactDate(model.date)} • ${model.timeZone}'),
                if (model.location.text.trim().isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Text(model.location.text.trim()),
                ],
              ],
            ),
          ),
        ),
        if (error != null) ...[
          const SizedBox(height: 16),
          Text(
            error!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        ],
      ],
    );
  }
}

class RoomFormModel {
  RoomFormModel({
    required String title,
    required this.date,
    required this.time,
    required this.timeZone,
    required String location,
    required String description,
    required this.theme,
  }) : title = TextEditingController(text: title),
       location = TextEditingController(text: location),
       description = TextEditingController(text: description);

  final TextEditingController title;
  DateTime date;
  ({int hour, int minute}) time;
  String timeZone;
  final TextEditingController location;
  final TextEditingController description;
  RoomTheme theme;

  RoomDraft get draft => RoomDraft(
    title: title.text,
    eventDate: date,
    eventTime: time,
    eventTimeZone: timeZone,
    location: location.text,
    description: description.text,
    theme: theme,
  );

  factory RoomFormModel.initial() {
    final tomorrow = DateTime.now().add(const Duration(days: 1));
    return RoomFormModel(
      title: '',
      date: DateTime(tomorrow.year, tomorrow.month, tomorrow.day),
      time: (hour: 19, minute: 0),
      timeZone: 'UTC',
      location: '',
      description: '',
      theme: roomThemeChoices.first,
    );
  }

  factory RoomFormModel.fromRoom(Room room) {
    final local = room.eventAt.toLocal();
    return RoomFormModel(
      title: room.title,
      date: DateTime(local.year, local.month, local.day),
      time: (hour: local.hour, minute: local.minute),
      timeZone: supportedTimeZones.contains(room.eventTimeZone)
          ? room.eventTimeZone
          : 'UTC',
      location: room.location ?? '',
      description: room.description ?? '',
      theme: room.theme,
    );
  }

  void dispose() {
    title.dispose();
    location.dispose();
    description.dispose();
  }
}

const supportedTimeZones = [
  'UTC',
  'Asia/Kolkata',
  'America/Los_Angeles',
  'America/New_York',
  'Europe/London',
  'Europe/Berlin',
  'Asia/Singapore',
  'Australia/Sydney',
];
