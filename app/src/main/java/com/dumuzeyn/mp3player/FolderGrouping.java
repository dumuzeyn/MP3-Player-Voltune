package com.dumuzeyn.mp3player;

import java.util.ArrayList;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FolderGrouping {
    Map<String, ArrayList<Track>> group(List<Track> tracks) {
        LinkedHashMap<String, ArrayList<Track>> result = new LinkedHashMap<>();
        for (Track track : tracks) {
            String folder = folderName(track.uri);
            ArrayList<Track> group = result.get(folder);
            if (group == null) {
                group = new ArrayList<>();
                result.put(folder, group);
            }
            group.add(track);
        }
        return result;
    }

    static String folderName(String rawUri) {
        try {
            if (rawUri == null || rawUri.trim().isEmpty()) {
                return "Unknown folder";
            }
            String path = rawUri;
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            int scheme = path.indexOf("://");
            if (scheme >= 0) {
                int firstPath = path.indexOf('/', scheme + 3);
                path = firstPath >= 0 ? path.substring(firstPath + 1) : "";
            }
            path = URLDecoder.decode(path, "UTF-8");
            if (path == null || path.trim().isEmpty()) {
                return "Unknown folder";
            }
            int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf(':'));
            String parent = separator > 0 ? path.substring(0, separator) : path;
            int parentSeparator = Math.max(parent.lastIndexOf('/'), parent.lastIndexOf(':'));
            String name = parent.substring(parentSeparator + 1).trim();
            return name.isEmpty() ? "Unknown folder" : name;
        } catch (Exception error) {
            return "Unknown folder";
        }
    }
}
