import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:hyped/features/rooms/data/cache/room_cache_database.dart';

class CacheIdentity {
  const CacheIdentity(this.accountId, this.sessionId, this.epoch);
  final String accountId;
  final String sessionId;
  final int epoch;
  @override
  String toString() => 'CacheIdentity[REDACTED]';
}

abstract interface class CacheIdentityStore {
  Future<({String account, String session})?> read();
  Future<void> write(String account, String session);
  Future<void> clear();
}

class SecureCacheIdentityStore implements CacheIdentityStore {
  SecureCacheIdentityStore(this.storage);
  final FlutterSecureStorage storage;
  static const key = 'room_cache.session_owner.v1';
  @override
  Future<({String account, String session})?> read() async {
    try {
      final value = await storage.read(key: key);
      if (value == null) return null;
      final json = jsonDecode(value) as Map;
      final account = json['account'] as String;
      final session = json['session'] as String;
      if (account.isEmpty || session.isEmpty) return null;
      return (account: account, session: session);
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> write(String account, String session) => storage.write(
    key: key,
    value: jsonEncode({'account': account, 'session': session}),
  );
  @override
  Future<void> clear() => storage.delete(key: key);
}

/// All cache operations and identity changes share one queue. Clearing fences HTTP results synchronously.
class RoomCacheSession {
  RoomCacheSession(this.database, this.identities);
  final RoomCacheDatabase database;
  final CacheIdentityStore identities;
  CacheIdentity? current;
  int _epoch = 0;
  int _readGeneration = 0;
  bool _blocked = false;
  bool _usable = true;
  bool _restoredFromDisk = false;
  Future<void> _queue = Future.value();
  Future<void>? _restoring;
  String? _restoringSession;
  int get epoch => _epoch;
  int get readGeneration => _readGeneration;
  void invalidateReads(CacheIdentity identity) {
    if (matches(identity)) _readGeneration++;
  }

  bool get blocked => _blocked;
  bool get restoredSession => !_blocked && _restoredFromDisk && current != null;
  Future<T> _serial<T>(Future<T> Function() action) {
    final operation = _queue.then((_) => action());
    _queue = operation.then<void>((_) {}, onError: (Object _, StackTrace _) {});
    return operation;
  }

  bool matches(CacheIdentity identity) =>
      !_blocked && current?.epoch == identity.epoch && _epoch == identity.epoch;
  Future<void> restore(String session) {
    if (_blocked || current?.sessionId == session) return Future.value();
    if (_restoringSession == session && _restoring != null) return _restoring!;
    final epoch = ++_epoch;
    current = null;
    _restoredFromDisk = false;
    _restoringSession = session;
    final operation = _serial(() async {
      final saved = await identities.read();
      if (epoch != _epoch || _blocked) return;
      if (saved == null || saved.session != session) {
        try {
          await identities.clear();
        } finally {
          await database.erase();
        }
        return;
      }
      current = CacheIdentity(saved.account, session, epoch);
      _restoredFromDisk = true;
    });
    _restoring = operation;
    return operation.whenComplete(() {
      if (identical(_restoring, operation)) {
        _restoring = null;
        _restoringSession = null;
      }
    });
  }

  // Only a newly authenticated exchange may reopen a cleared scope.
  Future<void> start(String account, String session) {
    _blocked = false;
    _restoredFromDisk = false;
    final epoch = ++_epoch;
    current = null;
    return _serial(() => _bind(account, session, epoch));
  }

  // An upgrade-time /me discovery cannot undo logout or an account change.
  Future<void> bind(String account, String session, int epoch) =>
      _serial(() => _bind(account, session, epoch));
  Future<void> _bind(String account, String session, int epoch) async {
    if (_blocked || epoch != _epoch) return;
    final saved = await identities.read();
    if (saved?.account != account || saved?.session != session) {
      try {
        await database.erase();
      } catch (_) {
        _usable = false;
      }
    }
    try {
      if (_usable) {
        await identities.write(account, session);
      } else {
        await identities.clear();
      }
    } catch (_) {
      _usable = false;
    }
    if (!_blocked && epoch == _epoch) {
      current = CacheIdentity(account, session, epoch);
    }
  }

  Future<void> clear() {
    ++_epoch;
    current = null;
    _blocked = true;
    _restoredFromDisk = false;
    return _serial(() async {
      try {
        await identities.clear();
      } finally {
        try {
          await database.erase();
        } catch (_) {
          _usable = false;
          rethrow;
        }
      }
    });
  }

  Future<T?> run<T>(CacheIdentity identity, Future<T> Function() action) =>
      _serial(() async {
        if (!_usable || !matches(identity)) return null;
        final value = await action();
        return matches(identity) ? value : null;
      });
}
