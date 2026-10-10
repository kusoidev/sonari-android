import 'package:flutter/foundation.dart';

import '../models/youtube_result.dart';
import '../services/api_service.dart';

/// Drives the Search tab: query text, results, loading and error state.
/// Named MusicSearchController to avoid clashing with Flutter's material
/// SearchController.
class MusicSearchController extends ChangeNotifier {
  String query = '';
  List<YouTubeSearchResult> results = [];
  bool isLoading = false;
  String? errorMessage;

  Future<void> search() async {
    final q = query.trim();
    if (q.isEmpty) return;

    isLoading = true;
    errorMessage = null;
    notifyListeners();

    try {
      results = await ApiService.search(q);
      if (results.isEmpty) errorMessage = 'No results found.';
    } catch (e) {
      errorMessage = e.toString();
      results = [];
    }
    isLoading = false;
    notifyListeners();
  }

  void clear() {
    query = '';
    results = [];
    errorMessage = null;
    notifyListeners();
  }
}