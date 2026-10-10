import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/download_service.dart';
import '../state/download_controller.dart';
import '../widgets/theme_background.dart';

/// Download tab. Port of the iOS DownloadView: paste a link, queue it, watch
/// the serial job queue progress.
class DownloadScreen extends StatefulWidget {
  final DownloadController controller;
  const DownloadScreen({super.key, required this.controller});

  @override
  State<DownloadScreen> createState() => _DownloadScreenState();
}

class _DownloadScreenState extends State<DownloadScreen> {
  DownloadController get _controller => widget.controller;
  final TextEditingController _textController = TextEditingController();

  @override
  void initState() {
    super.initState();
    DownloadService.instance.addListener(_onJobsChanged);
  }

  @override
  void dispose() {
    DownloadService.instance.removeListener(_onJobsChanged);
    _textController.dispose();
    super.dispose();
  }

  void _onJobsChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        const Positioned.fill(child: ThemeBackground()),
        SafeArea(
          bottom: false,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 12),
                Text('Downloads',
                    style: TextStyle(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        color: Colors.white)),
                const SizedBox(height: 20),
                Text('PASTE A YOUTUBE LINK',
                    style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: Colors.white.withValues(alpha: 0.4))),
                const SizedBox(height: 10),
                _inputRow(),
                const SizedBox(height: 16),
                _downloadButton(),
                const SizedBox(height: 20),
                if (_controller.jobs.isNotEmpty) ...[
                  _queueHeader(),
                  Expanded(child: _queueList()),
                ] else
                  const Spacer(),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _inputRow() {
    return Row(
      children: [
        Expanded(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(12),
            ),
            child: TextField(
              controller: _textController,
              style: const TextStyle(fontSize: 15, color: Colors.white),
              cursorColor: Colors.white,
              autocorrect: false,
              decoration: InputDecoration(
                border: InputBorder.none,
                hintText: 'youtube.com/watch?v=…',
                hintStyle:
                    TextStyle(color: Colors.white.withValues(alpha: 0.3)),
              ),
              onChanged: (v) {
                _controller.setUrlInput(v);
                setState(() {});
              },
            ),
          ),
        ),
        const SizedBox(width: 10),
        GestureDetector(
          onTap: () async {
            final data = await Clipboard.getData(Clipboard.kTextPlain);
            final text = data?.text;
            if (text != null) {
              _textController.text = text;
              _controller.setUrlInput(text);
              setState(() {});
            }
          },
          child: Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.08),
              shape: BoxShape.circle,
            ),
            child: Icon(Icons.content_paste,
                size: 18, color: Colors.white.withValues(alpha: 0.6)),
          ),
        ),
      ],
    );
  }

  Widget _downloadButton() {
    final isEmpty = _controller.urlInput.isEmpty;
    return GestureDetector(
      onTap: isEmpty
          ? null
          : () {
              _controller.pasteAndDownload();
              _textController.clear();
              setState(() {});
            },
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: isEmpty ? 0.4 : 1),
          borderRadius: BorderRadius.circular(24),
        ),
        child: const Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.download, size: 18, color: Colors.black),
            SizedBox(width: 8),
            Text('Add to Queue',
                style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Colors.black)),
          ],
        ),
      ),
    );
  }

  Widget _queueHeader() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        children: [
          Text('Queue',
              style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: Colors.white.withValues(alpha: 0.5))),
          const Spacer(),
          if (_controller.hasFinishedJobs)
            GestureDetector(
              onTap: () {
                _controller.removeFinished();
                setState(() {});
              },
              child: Text('Clear finished',
                  style: TextStyle(
                      fontSize: 12,
                      color: Colors.white.withValues(alpha: 0.4))),
            ),
        ],
      ),
    );
  }

  Widget _queueList() {
    return ListView.separated(
      padding: const EdgeInsets.only(bottom: 12),
      itemCount: _controller.jobs.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) => _queueRow(_controller.jobs[index]),
    );
  }

  Widget _queueRow(DownloadJob job) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Icon(_icon(job.state), size: 15, color: _iconColor(job.state)),
              const SizedBox(width: 8),
              Expanded(
                child: Text(job.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                        fontSize: 13,
                        color: Colors.white.withValues(alpha: 0.85))),
              ),
              _trailing(job),
            ],
          ),
          if (job.state == JobState.downloading) ...[
            const SizedBox(height: 8),
            ClipRRect(
              borderRadius: BorderRadius.circular(2),
              child: LinearProgressIndicator(
                value: job.progress,
                minHeight: 3,
                backgroundColor: Colors.white.withValues(alpha: 0.1),
                valueColor: const AlwaysStoppedAnimation(Colors.white),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _trailing(DownloadJob job) {
    switch (job.state) {
      case JobState.downloading:
        return Text('${(job.progress * 100).toInt()}%',
            style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Colors.white.withValues(alpha: 0.6)));
      case JobState.completed:
        return Text('Saved',
            style: TextStyle(
                fontSize: 12, color: Colors.green.withValues(alpha: 0.8)));
      case JobState.failed:
        return Text(job.error ?? 'Failed',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(
                fontSize: 12, color: Colors.red.withValues(alpha: 0.7)));
      default:
        return Text(job.state.label,
            style: TextStyle(
                fontSize: 12, color: Colors.white.withValues(alpha: 0.35)));
    }
  }

  IconData _icon(JobState state) {
    switch (state) {
      case JobState.queued:
        return Icons.schedule;
      case JobState.preparing:
        return Icons.sync;
      case JobState.downloading:
        return Icons.download;
      case JobState.completed:
        return Icons.check_circle;
      case JobState.failed:
        return Icons.warning;
    }
  }

  Color _iconColor(JobState state) {
    switch (state) {
      case JobState.completed:
        return Colors.green.withValues(alpha: 0.8);
      case JobState.failed:
        return Colors.red.withValues(alpha: 0.7);
      case JobState.downloading:
        return Colors.white;
      default:
        return Colors.white.withValues(alpha: 0.35);
    }
  }
}