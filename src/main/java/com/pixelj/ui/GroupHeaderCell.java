package com.pixelj.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * 分组标题单元格。
 * 用于显示日期分组或位置分组的标题。
 */
public class GroupHeaderCell extends Region {

    private final Label titleLabel;
    private final Label countLabel;
    private String groupId;

    public GroupHeaderCell() {
        setStyle("-fx-background-color: #2a2a2a;");

        titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        countLabel = new Label();
        countLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #999999;");

        VBox content = new VBox(4, titleLabel, countLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setLayoutX(16);
        content.setLayoutY(8);

        getChildren().add(content);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setCount(int count) {
        countLabel.setText(count + " 张照片");
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }
}
