package com.pixelj.ui;

import java.nio.file.Path;
import java.util.List;

/**
 * 显示项接口。
 * 支持图片和分组标题两种类型的显示项。
 */
public sealed interface DisplayItem permits DisplayItem.ImageItem, DisplayItem.HeaderItem {

    /**
     * 图片项。
     */
    record ImageItem(Path path) implements DisplayItem {
    }

    /**
     * 分组标题项。
     */
    record HeaderItem(String id, String title, int count, List<Path> paths) implements DisplayItem {
    }
}
