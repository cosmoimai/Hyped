import 'package:timezone/data/latest.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;
import 'package:hyped/features/media/presentation/room_photo_providers.dart';
import 'package:hyped/features/rooms/presentation/widgets/countdown_readout.dart';
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
    if (model == null && room != null && !controller.readOnly && room.canEdit) {
      model = RoomFormModel.fromRoom(room);
    }
    // Returning from Android's picker refreshes the room controller. Retain the
    // selection while that refresh temporarily replaces the form with loading UI.
    if (model case final editingModel?) {
      ref.watch(roomPhotoSelectionProvider(editingModel));
    }
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

class RoomFormScaffold extends ConsumerStatefulWidget {
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
  ConsumerState<RoomFormScaffold> createState() => _RoomFormScaffoldState();
}

class _RoomFormScaffoldState extends ConsumerState<RoomFormScaffold> {
  final formKey = GlobalKey<FormState>();

  @override
  Widget build(BuildContext context) {
    final photo = ref.watch(roomPhotoSelectionProvider(widget.model));
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
                                onPressed: widget.saving || photo.blocked
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
                  onPressed: widget.saving || photo.blocked
                      ? null
                      : () => widget.onStepChanged(widget.step - 1),
                  child: const Text('Back'),
                ),
                const SizedBox(width: 12),
              ],
              Expanded(
                child: FilledButton(
                  onPressed: widget.saving || photo.blocked
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

class _StyleStep extends ConsumerStatefulWidget {
  const _StyleStep({required this.model});

  final RoomFormModel model;

  @override
  ConsumerState<_StyleStep> createState() => _StyleStepState();
}

class _StyleStepState extends ConsumerState<_StyleStep> {
  @override
  Widget build(BuildContext context) {
    final model = widget.model;
    final photo = ref.watch(roomPhotoSelectionProvider(model));
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'LIVE CARD PREVIEW',
          style: Theme.of(context).textTheme.labelMedium,
        ),
        const SizedBox(height: 12),
        RoomThemeView(
          theme: model.theme,
          previewBytes: photo.selected?.bytes,
          scrim: true,
          child: DefaultTextStyle(
            style: Theme.of(
              context,
            ).textTheme.bodyMedium!.copyWith(color: Colors.white),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (model.location.text.trim().isNotEmpty)
                  Text(model.location.text.trim()),
                const SizedBox(height: 24),
                CountdownReadout(eventAt: model.previewInstant, onDark: true),
                const SizedBox(height: 24),
                const Text('UPCOMING MILESTONE'),
                const SizedBox(height: 8),
                Text(
                  model.title.text.trim().isEmpty
                      ? 'Your next milestone'
                      : model.title.text.trim(),
                  style: Theme.of(
                    context,
                  ).textTheme.headlineSmall?.copyWith(color: Colors.white),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 28),
        Text('1. Pastel tones', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 16),
        Wrap(
          spacing: 16,
          runSpacing: 16,
          children: [
            for (final theme in roomThemeChoices.where(
              (theme) => theme.kind == RoomThemeKind.preset,
            ))
              Semantics(
                label: roomThemeName(theme),
                selected:
                    model.theme.kind != RoomThemeKind.uploadedImage &&
                    theme.presetKey == model.theme.presetKey,
                button: true,
                child: InkWell(
                  onTap: photo.busy
                      ? null
                      : () {
                          photo.clear(restore: false);
                          setState(() => model.theme = theme);
                        },
                  borderRadius: BorderRadius.circular(24),
                  child: SizedBox(
                    width: 76,
                    child: Column(
                      children: [
                        Container(
                          width: 56,
                          height: 56,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            gradient: LinearGradient(
                              colors: colorsForTheme(theme),
                            ),
                            border: Border.all(
                              width: 3,
                              color:
                                  theme.presetKey == model.theme.presetKey &&
                                      model.theme.kind !=
                                          RoomThemeKind.uploadedImage
                                  ? Theme.of(context).colorScheme.primary
                                  : Colors.transparent,
                            ),
                          ),
                          child:
                              theme.presetKey == model.theme.presetKey &&
                                  model.theme.kind !=
                                      RoomThemeKind.uploadedImage
                              ? const Icon(
                                  Icons.check,
                                  color: Color(0xFF182033),
                                )
                              : null,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          roomThemeName(theme),
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
        const SizedBox(height: 28),
        Text(
          '2. Luminous gradients',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        for (final theme in roomThemeChoices.where(
          (theme) => theme.kind == RoomThemeKind.gradient,
        ))
          Semantics(
            label: roomThemeName(theme),
            selected:
                model.theme.kind != RoomThemeKind.uploadedImage &&
                theme.presetKey == model.theme.presetKey,
            child: Card(
              child: ListTile(
                leading: SizedBox.square(
                  dimension: 44,
                  child: RoomThemeView(
                    theme: theme,
                    padding: EdgeInsets.zero,
                    child: const SizedBox.expand(),
                  ),
                ),
                title: Text(roomThemeName(theme)),
                trailing:
                    theme.presetKey == model.theme.presetKey &&
                        model.theme.kind != RoomThemeKind.uploadedImage
                    ? const Icon(Icons.check_circle)
                    : const Icon(Icons.circle_outlined),
                onTap: photo.busy
                    ? null
                    : () {
                        photo.clear(restore: false);
                        setState(() => model.theme = theme);
                      },
              ),
            ),
          ),
        const SizedBox(height: 28),
        Text(
          '3. Custom memories',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        Card(
          child: ListTile(
            leading: const Icon(Icons.add_photo_alternate_outlined, size: 32),
            title: const Text('Choose a room photo'),
            subtitle: const Text('Your library · Static photo up to 5 MB'),
            trailing: const Icon(Icons.upload_outlined),
            onTap: photo.busy ? null : photo.pick,
          ),
        ),
        const Padding(
          padding: EdgeInsets.only(top: 8),
          child: Text(
            'Selected photos are uploaded privately and checked before they can appear in a room.',
          ),
        ),
        if (photo.busy) ...[
          const SizedBox(height: 12),
          const LinearProgressIndicator(),
          const Text('Preparing your photo securely…'),
        ],
        if (photo.error case final error?)
          Padding(padding: const EdgeInsets.only(top: 12), child: Text(error)),
        if (photo.ready)
          const Padding(
            padding: EdgeInsets.only(top: 12),
            child: Text('Photo ready. Continue to review your room.'),
          ),
        if (photo.selected != null)
          Wrap(
            spacing: 12,
            children: [
              if (!photo.ready)
                TextButton(
                  onPressed: photo.busy ? null : photo.retry,
                  child: const Text('Retry photo upload'),
                ),
              TextButton(
                onPressed: photo.busy ? null : photo.clear,
                child: const Text('Discard selected photo'),
              ),
            ],
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
  static final bool _zonesReady = (() {
    tzdata.initializeTimeZones();
    return true;
  })();
  DateTime get previewInstant {
    assert(_zonesReady);
    // Referencing the lazy initializer is required in release too.
    if (!_zonesReady) {
      return DateTime.utc(
        date.year,
        date.month,
        date.day,
        time.hour,
        time.minute,
      );
    }
    return tz.TZDateTime(
      timeZone == 'UTC' ? tz.UTC : tz.getLocation(timeZone),
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    ).toUtc();
  }

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
    if (!_zonesReady) throw StateError('Time zones are unavailable.');
    final timeZone = supportedTimeZones.contains(room.eventTimeZone)
        ? room.eventTimeZone
        : 'UTC';
    final local = tz.TZDateTime.from(
      room.eventAt,
      timeZone == 'UTC' ? tz.UTC : tz.getLocation(timeZone),
    );
    return RoomFormModel(
      title: room.title,
      date: DateTime(local.year, local.month, local.day),
      time: (hour: local.hour, minute: local.minute),
      timeZone: timeZone,
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
