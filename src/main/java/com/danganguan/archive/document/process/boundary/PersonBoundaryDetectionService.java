package com.danganguan.archive.document.process.boundary;

import java.util.List;

public interface PersonBoundaryDetectionService {
    List<BoundaryGroup> detect(List<BoundaryImage> images);
}
