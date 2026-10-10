import 'dart:io';

import 'package:flutter/material.dart';

import '../services/thumbnail_cache.dart';

/// A thumbnail that checks the local disk cache first, then falls back to a
/// network fetch that also caches. Port of the iOS CachedAsyncImage, simplified
/// for Flutter's image pipeline.
class CachedThumbnail extends StatefulWidget {
  final String? youtubeID;
  final String? remoteURL;
  final double width;
  final double height;
  final double radius;
  final double iconSize;

  const CachedThumbnail({
    super.key,
    required this.youtubeID,
    required this.remoteURL,
    required this.width,
    required this.height,
    this.radius = 8,
    this.iconSize = 16,
  });

  @override
  State<CachedThumbnail> createState() => _CachedThumbnailState();
}

class _CachedThumbnailState extends State<CachedThumbnail> {
  File? _file;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(covariant CachedThumbnail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.youtubeID != widget.youtubeID) {
      _file = null;
      _load();
    }
  }

  Future<void> _load() async {
    final id = widget.youtubeID;
    if (id == null || id.isEmpty) return;

    final cached = await ThumbnailCache.instance.cachedFile(id);
    if (cached != null) {
      if (mounted) setState(() => _file = cached);
      return;
    }

    // Not cached yet: kick off a background download that populates the disk
    // cache, then swap in the local file. Until then the network image or the
    // placeholder renders.
    final remote = widget.remoteURL;
    if (remote != null && remote.isNotEmpty) {
      final downloaded = await ThumbnailCache.instance.ensureCached(id, remote);
      if (mounted && downloaded != null) {
        setState(() => _file = downloaded);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(widget.radius),
      child: SizedBox(
        width: widget.width,
        height: widget.height,
        child: _buildImage(),
      ),
    );
  }

  Widget _buildImage() {
    if (_file != null) {
      return Image.file(
        _file!,
        width: widget.width,
        height: widget.height,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => _placeholder(),
      );
    }

    final remote = widget.remoteURL;
    if (remote != null && remote.isNotEmpty) {
      return Image.network(
        remote,
        width: widget.width,
        height: widget.height,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => _placeholder(),
        loadingBuilder: (context, child, progress) {
          if (progress == null) return child;
          return _placeholder();
        },
      );
    }

    return _placeholder();
  }

  Widget _placeholder() {
    return Container(
      width: widget.width,
      height: widget.height,
      color: Colors.white.withValues(alpha: 0.07),
      alignment: Alignment.center,
      child: Icon(
        Icons.music_note,
        size: widget.iconSize,
        color: Colors.white.withValues(alpha: 0.25),
      ),
    );
  }
}