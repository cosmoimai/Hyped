import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mocktail/mocktail.dart';
import 'package:hyped/features/authentication/data/auth_repository.dart';
import 'package:hyped/features/authentication/presentation/controllers/auth_controller.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hyped/app/bootstrap/providers.dart';
import 'package:hyped/features/media/data/room_photo_api.dart';
import 'package:hyped/features/media/presentation/room_photo_providers.dart';
import 'package:hyped/features/rooms/domain/room.dart';
import 'package:hyped/features/rooms/presentation/screens/room_form_screen.dart';
import 'helpers/fakes.dart';

const asset = '019b1f2b-d5e2-7d46-9165-a37ed59d6080';
final photo = PickedRoomPhoto(Uint8List.fromList([255, 216, 255, 217]), 16, 12);

class MockPhotoAuthRepository extends Mock implements AuthRepository {}

class Picker extends RoomPhotoPicker {
  Future<PickedRoomPhoto?> Function() result = () async => photo;
  @override
  Future<PickedRoomPhoto?> pick() => result();
}

class PhotoApi extends RoomPhotoApi {
  PhotoApi() : super(Dio());
  int uploads = 0;
  Future<String> Function() result = () async => asset;
  @override
  Future<String> upload(
    PickedRoomPhoto photo,
    String session,
    CancelToken cancel,
  ) async {
    uploads++;
    return result();
  }
}

class Adapter implements HttpClientAdapter {
  Adapter(this.handle);
  final Future<ResponseBody> Function(RequestOptions options) handle;
  final requests = <RequestOptions>[];
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? stream,
    Future<void>? cancel,
  ) async {
    requests.add(options);
    if (stream != null) await stream.drain<void>();
    return handle(options);
  }

  @override
  void close({bool force = false}) {}
}

ResponseBody jsonBody(Object data, [int status = 200]) =>
    ResponseBody.fromString(
      jsonEncode(data),
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late RoomFormModel model;
  late PhotoApi api;
  late Picker picker;
  late MemoryTokenStore store;
  late RoomPhotoSelection selection;
  int epoch = 0;
  setUp(() {
    model = RoomFormModel.initial();
    api = PhotoApi();
    picker = Picker();
    store = MemoryTokenStore(tokens());
    epoch = 0;
    selection = RoomPhotoSelection(api, picker, store, () => epoch, model);
  });
  tearDown(() {
    selection.dispose();
    model.dispose();
  });

  test(
    'cancelled picker preserves the current theme and makes no upload',
    () async {
      final original = model.theme;
      picker.result = () async => null;
      await selection.pick();
      expect(model.theme, original);
      expect(api.uploads, 0);
      expect(selection.blocked, isFalse);
    },
  );
  test(
    'ready upload creates only a media reference and can be discarded',
    () async {
      final original = model.theme;
      await selection.pick();
      expect(selection.ready, isTrue);
      expect(selection.blocked, isFalse);
      expect(model.theme.toJson(), {
        'kind': 'UPLOADED_IMAGE',
        'mediaAssetId': asset,
        'overlayKey': 'image-dark-40',
      });
      selection.clear();
      expect(model.theme, original);
      expect(selection.selected, isNull);
    },
  );
  for (final code in ['MEDIA_UNAVAILABLE', 'MANUAL_REVIEW', 'PHOTO_REJECTED']) {
    test(
      '$code keeps the previous theme and blocks saving the selected preview',
      () async {
        final original = model.theme;
        api.result = () async => throw RoomPhotoException(code);
        await selection.pick();
        expect(selection.blocked, isTrue);
        expect(model.theme, original);
        expect(selection.error, isNotNull);
        expect(selection.error, isNot(contains(asset)));
        selection.clear();
        expect(selection.blocked, isFalse);
      },
    );
  }
  test('failed upload can retry without asking for another photo', () async {
    api.result = () async => throw const RoomPhotoException();
    await selection.pick();
    api.result = () async => asset;
    await selection.retry();
    expect(selection.ready, isTrue);
    expect(api.uploads, 2);
  });
  for (final phase in ['picker', 'upload']) {
    for (final change in ['logout', 'account', 'epoch']) {
      test(
        '$change while $phase is pending never attaches old-account bytes',
        () async {
          final gate = Completer<void>();
          if (phase == 'picker') {
            picker.result = () async {
              await gate.future;
              return photo;
            };
          } else {
            api.result = () async {
              await gate.future;
              return asset;
            };
          }
          final operation = selection.pick();
          await Future<void>.delayed(Duration.zero);
          if (change == 'logout') {
            await store.clear();
          } else if (change == 'account') {
            await store.write(tokens('two'));
          } else {
            epoch++;
          }
          gate.complete();
          await operation;
          expect(selection.selected, isNull);
          expect(model.theme.kind, isNot(RoomThemeKind.uploadedImage));
        },
      );
    }
  }
  test(
    'preview instants honor IANA zone independent of the phone timezone',
    () {
      model.date = DateTime(2030, 1, 1);
      model.time = (hour: 10, minute: 0);
      model.timeZone = 'Asia/Kolkata';
      expect(model.previewInstant, DateTime.utc(2030, 1, 1, 4, 30));
      model.timeZone = 'America/New_York';
      expect(model.previewInstant, DateTime.utc(2030, 1, 1, 15));
      model.date = DateTime(2030, 7, 1);
      expect(model.previewInstant, DateTime.utc(2030, 7, 1, 14));
    },
  );
  test(
    'signed object transfer has no auth interceptors or JWT headers',
    () async {
      final auth = Dio(
        BaseOptions(
          baseUrl: 'https://api.test',
          headers: {'Authorization': 'Bearer fixture'},
        ),
      );
      final transfer = Dio();
      final url =
          'https://${'a' * 32}.r2.cloudflarestorage.com/private/quarantine/$asset?fixture=1';
      final authAdapter = Adapter((options) async {
        expect(options.extra['expectedSessionId'], 'session-one');
        if (options.path.endsWith('/complete')) {
          return jsonBody({'status': 'READY'});
        }
        return jsonBody({
          'mediaAssetId': asset,
          'upload': {
            'method': 'PUT',
            'url': url,
            'headers': {
              'Content-Type': 'image/jpeg',
              'Content-Length': '${photo.bytes.length}',
            },
            'maximumBytes': 1572864,
            'expiresAt': DateTime.now()
                .add(const Duration(minutes: 10))
                .toIso8601String(),
          },
        });
      });
      final transferAdapter = Adapter((options) async {
        expect(
          options.headers.keys.map((key) => key.toLowerCase()),
          isNot(contains('authorization')),
        );
        expect(options.followRedirects, isFalse);
        return ResponseBody.fromString('', 200);
      });
      auth.httpClientAdapter = authAdapter;
      transfer.httpClientAdapter = transferAdapter;
      expect(
        await RoomPhotoApi(
          auth,
          transfer,
        ).upload(photo, 'session-one', CancelToken()),
        asset,
      );
      expect(transfer.interceptors, isEmpty);
      expect(transferAdapter.requests.length, 1);
      expect(authAdapter.requests.length, 2);
    },
  );
  test(
    'storage URL validation rejects foreign hosts, HTTP, user info and ports',
    () {
      for (final url in [
        'http://${'a' * 32}.r2.cloudflarestorage.com/x',
        'https://evil.test/x',
        'https://user@${'a' * 32}.r2.cloudflarestorage.com/x',
        'https://${'a' * 32}.r2.cloudflarestorage.com:8443/x',
      ]) {
        expect(RoomPhotoApi.storageUrl(url), isFalse);
      }
      expect(const RoomPhotoException().toString(), isNot(contains('http')));
      expect(photo.toString(), 'PickedRoomPhoto[REDACTED]');
    },
  );
  testWidgets(
    'Style exposes system photo selection and keeps Continue blocked until ready',
    (tester) async {
      final scopeModel = RoomFormModel.initial();
      int step = 1;
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authControllerProvider.overrideWith(
              (ref) => AuthController(MockPhotoAuthRepository()),
            ),
            roomPhotoApiProvider.overrideWithValue(api),
            roomPhotoPickerProvider.overrideWithValue(picker),
            tokenStoreProvider.overrideWithValue(store),
          ],
          child: MaterialApp(
            home: StatefulBuilder(
              builder: (context, update) => RoomFormScaffold(
                title: 'Create room',
                step: step,
                model: scopeModel,
                error: null,
                saving: false,
                onStepChanged: (value) => update(() => step = value),
                onSubmit: () async {},
              ),
            ),
          ),
        ),
      );
      api.result = () async =>
          throw const RoomPhotoException('MEDIA_UNAVAILABLE');
      await tester.scrollUntilVisible(find.text('Choose a room photo'), 250);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Choose a room photo'));
      await tester.pumpAndSettle();
      expect(
        find.textContaining('Photo sharing needs cloud storage'),
        findsOneWidget,
      );
      final continueButton = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, 'Continue'),
      );
      expect(continueButton.onPressed, isNull);
      await tester.ensureVisible(find.text('Discard selected photo'));
      await tester.tap(find.text('Discard selected photo'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, 'Continue'))
            .onPressed,
        isNotNull,
      );
      await tester.pumpWidget(const SizedBox());
      scopeModel.dispose();
    },
  );
  test('photo model survives room JSON and stores no signed delivery URL', () {
    final theme = RoomTheme.fromJson({
      'kind': 'UPLOADED_IMAGE',
      'mediaAssetId': asset,
      'overlayKey': 'image-dark-40',
    });
    expect(theme.mediaAssetId, asset);
    expect(theme.toJson().keys, isNot(contains('url')));
  });
}
