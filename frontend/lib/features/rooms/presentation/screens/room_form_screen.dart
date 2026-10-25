import 'package:hyped/features/rooms/presentation/widgets/room_app_bar.dart';
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
      return Scaffold(
        appBar: RoomAppBar(
          title: 'Edit room',
          backLocation: '/rooms/${widget.roomId}',
        ),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    if (room == null) {
      return Scaffold(
        appBar: RoomAppBar(
          title: 'Edit room',
          backLocation: '/rooms/${widget.roomId}',
        ),
        body: Center(child: Text(controller.error ?? 'Room unavailable.')),
      );
    }
    if (controller.readOnly) {
      return Scaffold(
        appBar: RoomAppBar(
          title: 'Edit room',
          backLocation: '/rooms/${widget.roomId}',
        ),
        body: const Center(
          child: Text(
            'Cached rooms are read-only. Refresh the room when connected before editing.',
          ),
        ),
      );
    }
    if (!room.canEdit) {
      return Scaffold(
        appBar: RoomAppBar(
          title: 'Edit room',
          backLocation: '/rooms/${widget.roomId}',
        ),
        body: Center(
          child: Text(
            room.status == RoomStatus.archived
                ? 'Archived rooms cannot be edited.'
                : 'Only hosts can edit this room.',
          ),
        ),
      );
    }
    model ??= RoomFormModel.fromRoom(room);
    return RoomFormScaffold(
      title: 'Edit room',
      backLocation: '/rooms/${widget.roomId}',
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
    this.backLocation = '/home',
    required this.step,
    required this.model,
    required this.error,
    required this.saving,
    required this.onStepChanged,
    required this.onSubmit,
    super.key,
  });

  final String title;
  final String backLocation;
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
      appBar: RoomAppBar(
        title: widget.title,
        backLocation: widget.backLocation,
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
          child: Form(
            key: formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  'STEP ${widget.step + 1} OF 3',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w700,
                    fontSize: 12,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  const [
                    'Event details',
                    'Make it yours',
                    'Ready to count down?',
                  ][widget.step],
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
                const SizedBox(height: 20),
                Row(
                  children: [
                    for (var index = 0; index < 3; index++)
                      Expanded(
                        child: Padding(
                          padding: EdgeInsets.only(right: index == 2 ? 0 : 8),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Container(
                                height: 4,
                                decoration: BoxDecoration(
                                  color: index <= widget.step
                                      ? Theme.of(context).colorScheme.primary
                                      : Theme.of(context).colorScheme.primary
                                            .withValues(alpha: .15),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                              ),
                              TextButton(
                                onPressed: widget.saving
                                    ? null
                                    : () {
                                        if (index > widget.step &&
                                            widget.step == 0 &&
                                            !(formKey.currentState
                                                    ?.validate() ??
                                                false)) {
                                          return;
                                        }
                                        widget.onStepChanged(index);
                                      },
                                child: Text(
                                  const ['Details', 'Style', 'Review'][index],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 16),
                if (widget.step == 0) _DetailsStep(model: widget.model),
                if (widget.step == 1) ...[
                  const Text('Choose a color mood for your countdown.'),
                  const SizedBox(height: 20),
                  _StyleStep(model: widget.model),
                ],
                if (widget.step == 2)
                  _ReviewStep(model: widget.model, error: widget.error),
                const SizedBox(height: 24),
                Text(
                  'Your countdown stays private to your room.',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ),
      bottomNavigationBar: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 16),
          child: Row(
            children: [
              if (widget.step > 0) ...[
                TextButton(
                  onPressed: widget.saving
                      ? null
                      : () => widget.onStepChanged(widget.step - 1),
                  child: const Text('Back'),
                ),
                const SizedBox(width: 12),
              ],
              Expanded(
                child: FilledButton(
                  onPressed: widget.saving
                      ? null
                      : () async {
                          if (widget.step == 0 &&
                              !(formKey.currentState?.validate() ?? false)) {
                            return;
                          }
                          if (widget.step == 2) {
                            await widget.onSubmit();
                          } else {
                            widget.onStepChanged(widget.step + 1);
                          }
                        },
                  child: widget.saving
                      ? const SizedBox.square(
                          dimension: 20,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(widget.step == 2 ? 'Save room' : 'Continue'),
                ),
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
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            TextFormField(
              controller: model.title,
              decoration: const InputDecoration(
                labelText: 'Title',
                hintText: 'Something worth looking forward to',
                prefixIcon: Icon(Icons.edit_outlined),
              ),
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
                        lastDate: DateTime.now().add(
                          const Duration(days: 3650),
                        ),
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
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Timezone'),
              items: supportedTimeZones
                  .map(
                    (zone) => DropdownMenuItem(value: zone, child: Text(zone)),
                  )
                  .toList(growable: false),
              onChanged: (value) =>
                  setState(() => model.timeZone = value ?? 'UTC'),
            ),
            const SizedBox(height: 20),
            TextFormField(
              controller: model.location,
              decoration: const InputDecoration(
                labelText: 'Location (optional)',
              ),
              maxLength: 120,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: model.description,
              decoration: const InputDecoration(
                labelText: 'Description (optional)',
              ),
              maxLength: 500,
              maxLines: 3,
            ),
          ],
        ),
      ),
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
          Semantics(
            label: roomThemeName(theme),
            selected: theme.presetKey == widget.model.theme.presetKey,
            button: true,
            child: InkWell(
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
                  Positioned(
                    left: 12,
                    right: 12,
                    bottom: 12,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 6,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFF182033),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        roomThemeName(theme),
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 12,
                        ),
                      ),
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
          scrim: true,
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
