/// A user-created named group of favorited words (e.g. "Seyahat", "İş"),
/// so favorites can be organized and a specific group can be shown on the
/// home-screen widget instead of the whole favorites list.
class FavoriteGroup {
  final String id;
  final String name;
  final Set<String> wordIds;

  const FavoriteGroup({required this.id, required this.name, required this.wordIds});

  FavoriteGroup copyWith({String? name, Set<String>? wordIds}) => FavoriteGroup(
        id: id,
        name: name ?? this.name,
        wordIds: wordIds ?? this.wordIds,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'wordIds': wordIds.toList(),
      };

  factory FavoriteGroup.fromJson(Map<String, dynamic> json) => FavoriteGroup(
        id: json['id'] as String,
        name: json['name'] as String,
        wordIds: (json['wordIds'] as List<dynamic>).cast<String>().toSet(),
      );
}
