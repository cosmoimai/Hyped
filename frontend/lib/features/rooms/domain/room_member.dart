import 'package:hyped/features/rooms/domain/room.dart';

class RoomMember {
  const RoomMember({
    required this.userId,
    required this.displayName,
    required this.role,
    this.isCurrentUser = false,
  });
  final String userId;
  final String displayName;
  final RoomRole role;
  final bool isCurrentUser;
  factory RoomMember.fromJson(Map<String, dynamic> json) => RoomMember(
    userId: json['userId'] as String,
    displayName: json['displayName'] as String,
    role: RoomRoleX.fromWire(json['role'] as String),
    isCurrentUser: json['isCurrentUser'] == true,
  );
}

class MemberSnapshot {
  const MemberSnapshot(this.items, this.callerRole, this.active, this.etag);
  final List<RoomMember> items;
  final RoomRole callerRole;
  final bool active;
  final String etag;
}

enum MemberAction { promote, demote, remove, transfer }

List<MemberAction> memberActions(MemberSnapshot snapshot, RoomMember member) {
  if (!snapshot.active ||
      member.isCurrentUser ||
      member.role == RoomRole.owner) {
    return const [];
  }
  if (snapshot.callerRole == RoomRole.owner) {
    return [
      member.role == RoomRole.member
          ? MemberAction.promote
          : MemberAction.demote,
      MemberAction.transfer,
      MemberAction.remove,
    ];
  }
  if (snapshot.callerRole == RoomRole.coHost &&
      member.role == RoomRole.member) {
    return const [MemberAction.remove];
  }
  return const [];
}
