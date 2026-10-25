import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hyped/app/theme/hyped_theme.dart';
import 'package:hyped/features/home/presentation/screens/home_screen.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/controllers/room_providers.dart';
import 'package:hyped/features/rooms/presentation/screens/room_detail_screen.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';
import 'package:hyped/features/rooms/presentation/widgets/countdown_readout.dart';
import 'package:hyped/features/rooms/presentation/widgets/room_theme_view.dart';
import 'room_widgets_test.dart' as fixtures;

void main() {
  final instant = DateTime.utc(2030, 1, 1);
  for (final delta in [-1, 0, 1, 1000, 60000, 86400000]) {
    testWidgets('countdown boundary at $delta ms', (tester) async {
      final event = instant.add(Duration(milliseconds: delta));
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: CountdownReadout(eventAt: event, clock: () => instant),
          ),
        ),
      );
      if (delta <= 0) {
        expect(find.text('Event started'), findsOneWidget);
      } else {
        final semantics = tester.ensureSemantics();
        expect(
          find.bySemanticsLabel(
            delta >= 86400000
                ? '1 days, 0 hours, 0 minutes, 0 seconds remaining'
                : delta >= 60000
                ? '0 days, 0 hours, 1 minutes, 0 seconds remaining'
                : '0 days, 0 hours, 0 minutes, 1 seconds remaining',
          ),
          findsOneWidget,
        );
        semantics.dispose();
      }
    });
  }

  testWidgets('countdown ticks to event time and disposes its timer', (
    tester,
  ) async {
    var now = instant;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CountdownReadout(
            eventAt: instant.add(const Duration(seconds: 1)),
            clock: () => now,
          ),
        ),
      ),
    );
    now = instant.add(const Duration(seconds: 1));
    await tester.pump(const Duration(seconds: 1));
    expect(find.text('Event started'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 5));
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'countdown uses instants across timezone offsets and resumes after edits',
    (tester) async {
      var event = DateTime.parse('2030-01-01T05:30:00+05:30');
      late StateSetter update;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) {
                update = setState;
                return CountdownReadout(eventAt: event, clock: () => instant);
              },
            ),
          ),
        ),
      );
      expect(find.text('Event started'), findsOneWidget);
      update(() => event = instant.add(const Duration(hours: 1)));
      await tester.pump();
      expect(find.text('Event started'), findsNothing);
      expect(find.text('01'), findsOneWidget);
    },
  );

  testWidgets('countdown refreshes from device clock on resume', (
    tester,
  ) async {
    var now = instant;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CountdownReadout(
            eventAt: instant.add(const Duration(hours: 1)),
            clock: () => now,
          ),
        ),
      ),
    );
    now = instant.add(const Duration(hours: 2));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(find.text('Event started'), findsOneWidget);
  });

  for (final dark in [false, true]) {
    testWidgets(
      'narrow large-text home and room work in ${dark ? 'dark' : 'light'} mode',
      (tester) async {
        tester.view.physicalSize = const Size(320, 800);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final room = fixtures.room(
          title: 'A very long milestone title with friends and family',
        );
        final repo = fixtures.repositoryWithRooms([room]);
        Widget app(Widget screen) => ProviderScope(
          overrides: [roomRepositoryProvider.overrideWithValue(repo)],
          child: MaterialApp(
            theme: dark ? HypedTheme.dark : HypedTheme.light,
            builder: (_, child) => MediaQuery(
              data: MediaQueryData(
                size: const Size(320, 800),
                textScaler: TextScaler.linear(2),
              ),
              child: child!,
            ),
            home: screen,
          ),
        );
        await tester.pumpWidget(app(const HomeScreen()));
        await tester.pumpAndSettle();
        await tester.drag(find.byType(Scrollable).first, const Offset(0, -350));
        await tester.pump();
        expect(tester.takeException(), isNull);
        await tester.pumpWidget(const SizedBox());
        await tester.pumpWidget(
          ProviderScope(
            overrides: [
              roomRepositoryProvider.overrideWithValue(
                fixtures.repositoryWithDetail(room),
              ),
            ],
            child: MaterialApp(
              theme: dark ? HypedTheme.dark : HypedTheme.light,
              builder: (_, child) => MediaQuery(
                data: MediaQueryData(
                  size: const Size(320, 800),
                  textScaler: TextScaler.linear(2),
                ),
                child: child!,
              ),
              home: const RoomDetailScreen(roomId: 'room-id'),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.byTooltip('Back'), findsOneWidget);
        expect(tester.takeException(), isNull);
      },
    );
  }

  for (final destination in [
    '/join',
    '/rooms/new',
    '/settings',
    '/widgets/room',
  ]) {
    testWidgets('home routes to $destination', (tester) async {
      final router = GoRouter(
        initialLocation: '/home',
        routes: [
          GoRoute(path: '/home', builder: (_, _) => const HomeScreen()),
          GoRoute(
            path: destination,
            builder: (_, _) => const Scaffold(body: Text('Destination')),
          ),
        ],
      );
      addTearDown(router.dispose);
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            roomRepositoryProvider.overrideWithValue(
              fixtures.repositoryWithRooms([]),
            ),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();
      switch (destination) {
        case '/join':
          await tester.tap(find.text('Join'));
        case '/rooms/new':
          await tester.tap(find.byTooltip('Create room'));
        case '/settings':
          await tester.tap(find.text('Profile'));
        case '/widgets/room':
          await tester.tap(find.byTooltip('More options'));
          await tester.pumpAndSettle();
          await tester.tap(find.text('Countdown widgets'));
      }
      await tester.pumpAndSettle();
      expect(find.text('Destination'), findsOneWidget);
    });
  }

  testWidgets('archived rooms remain accessible in their own section', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWithValue(
            fixtures.repositoryWithRooms([
              fixtures
                  .room(title: 'Past trip')
                  .copyWith(status: RoomStatus.archived),
            ]),
          ),
        ],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Archived (1)'), findsOneWidget);
    expect(find.text('Past trip'), findsNothing);
    await tester.tap(find.text('Archived (1)'));
    await tester.pumpAndSettle();
    expect(find.text('Past trip'), findsOneWidget);
  });

  testWidgets('form retains title and canonical peach theme through review', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          roomRepositoryProvider.overrideWithValue(
            fixtures.repositoryWithRooms([]),
          ),
        ],
        child: MaterialApp(
          theme: HypedTheme.light,
          home: const CreateRoomScreen(),
        ),
      ),
    );
    await tester.enterText(
      find.byType(TextFormField).first,
      'Our next adventure',
    );
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();
    await tester.ensureVisible(find.text('Peach gold'));
    await tester.tap(find.text('Peach gold'));
    await tester.pump();
    expect(
      tester
          .getSemantics(
            find.byWidgetPredicate(
              (widget) =>
                  widget is Semantics &&
                  widget.properties.label == 'Peach gold',
            ),
          )
          .flagsCollection
          .isSelected
          .toBoolOrNull(),
      isTrue,
    );
    await tester.tap(find.text('Continue'));
    await tester.pumpAndSettle();
    expect(find.text('Our next adventure'), findsOneWidget);
    expect(
      tester.widget<RoomThemeView>(find.byType(RoomThemeView)).theme.presetKey,
      'peach-gold-01',
    );
    expect(find.text('Save room'), findsOneWidget);
  });

  test('fonts and distribution notices are bundled', () async {
    TestWidgetsFlutterBinding.ensureInitialized();
    for (final family in ['Manrope', 'Outfit']) {
      for (final weight
          in family == 'Manrope'
              ? [400, 500, 600, 700, 800]
              : [400, 500, 600, 700]) {
        expect(
          (await rootBundle.load(
            'assets/fonts/$family-$weight.ttf',
          )).lengthInBytes,
          greaterThan(10000),
        );
      }
      expect(
        await rootBundle.loadString('assets/fonts/$family-OFL.txt'),
        contains('SIL OPEN FONT LICENSE'),
      );
    }
    expect(HypedTheme.light.textTheme.bodyMedium?.fontFamily, 'Manrope');
    expect(HypedTheme.dark.textTheme.headlineLarge?.fontFamily, 'Outfit');
  });
}
