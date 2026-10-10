/// A single timed lyric line. startTime/endTime are kept for tap-to-seek and
/// future karaoke-style highlighting; time is the line's start in seconds.
class LyricLine {
  final String id;
  final double time;
  final String text;
  final double startTime;
  final double? endTime;

  LyricLine({
    String? id,
    required this.time,
    required this.text,
    required this.startTime,
    this.endTime,
  }) : id = id ?? '${DateTime.now().microsecondsSinceEpoch}-${text.hashCode}';

  Map<String, dynamic> toJson() => {
        'time': time,
        'text': text,
        'startTime': startTime,
        'endTime': endTime,
      };

  factory LyricLine.fromJson(Map<String, dynamic> json) {
    return LyricLine(
      time: (json['time'] as num?)?.toDouble() ?? 0,
      text: json['text'] as String? ?? '',
      startTime: (json['startTime'] as num?)?.toDouble() ?? 0,
      endTime: (json['endTime'] as num?)?.toDouble(),
    );
  }

  LyricLine copyWith({String? text}) {
    return LyricLine(
      id: id,
      time: time,
      text: text ?? this.text,
      startTime: startTime,
      endTime: endTime,
    );
  }
}