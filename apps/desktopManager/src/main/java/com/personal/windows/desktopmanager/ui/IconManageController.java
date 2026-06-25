package com.personal.windows.desktopmanager.ui;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.personal.windows.desktopmanager.exception.BusinessException;
import com.personal.windows.desktopmanager.model.DesktopFileVo;
import com.personal.windows.desktopmanager.model.GroupDto;
import com.personal.windows.desktopmanager.model.GroupVo;
import com.personal.windows.desktopmanager.service.IGroupService;

public class IconManageController {

    private static final Logger log = LoggerFactory.getLogger(IconManageController.class);

    @FXML
    private ListView<GroupVo> groupListView;

    @FXML
    private ListView<DesktopFileVo> fileListView;

    @FXML
    private Label currentGroupLabel;

    @FXML
    private Label fileCountLabel;

    @FXML
    private Label emptyHintLabel;

    @FXML
    private Button addGroupButton;

    private IGroupService groupService;
    private Stage stage;

    private final ObservableList<GroupVo> groupItems = FXCollections.observableArrayList();
    private final ObservableList<DesktopFileVo> fileItems = FXCollections.observableArrayList();

    private String currentSelectedGroupId;

    public void setGroupService(IGroupService groupService) {
        this.groupService = groupService;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        configureGroupListView();
        configureFileListView();
    }

    public void loadData() {
        if (groupService == null) {
            log.warn("loadData: groupService 尚未初始化");
            return;
        }
        refreshGroupList();
        if (groupItems.isEmpty()) {
            currentGroupLabel.setText("分组名：无分组");
            fileCountLabel.setText("共 0 个文件");
            emptyHintLabel.setVisible(true);
            fileListView.setVisible(false);
            return;
        }
        groupListView.getSelectionModel().select(0);
        GroupVo selected = groupListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            loadFilesForGroup(selected.getId());
        }
    }

    private void configureGroupListView() {
        groupListView.setItems(groupItems);

        groupListView.setCellFactory(lv -> {
            ListCell<GroupVo> cell = new ListCell<>() {
                @Override
                protected void updateItem(GroupVo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setContextMenu(null);
                    } else {
                        setText(item.getName() + " (" + item.getFileCount() + ")");
                        setContextMenu(createGroupContextMenu(item));
                    }
                }
            };

            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            cell.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasString()) {
                    String filePath = db.getString();
                    GroupVo targetGroup = cell.getItem();
                    if (targetGroup != null) {
                        moveFileToGroup(filePath, targetGroup.getId());
                        success = true;
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            return cell;
        });

        groupListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) {
                        currentSelectedGroupId = newVal.getId();
                        loadFilesForGroup(newVal.getId());
                    }
                });
    }

    private void configureFileListView() {
        fileListView.setItems(fileItems);

        fileListView.setCellFactory(lv -> {
            ListCell<DesktopFileVo> cell = new ListCell<>() {
                @Override
                protected void updateItem(DesktopFileVo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        String prefix = item.isDirectory() ? "📁 " : "📄 ";
                        setText(prefix + item.getFileName());
                    }
                }
            };

            cell.setOnDragDetected(event -> {
                if (cell.getItem() == null) {
                    return;
                }
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(cell.getItem().getFilePath());
                db.setContent(content);
                event.consume();
            });

            cell.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && cell.getItem() != null) {
                    executeFile(cell.getItem().getFilePath());
                }
            });

            return cell;
        });
    }

    private ContextMenu createGroupContextMenu(GroupVo group) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("group-context-menu");

        boolean isDefaultGroup = groupService != null
                && group.getId().equals(groupService.getDefaultGroupId());

        MenuItem renameItem = new MenuItem("重命名");
        renameItem.setOnAction(e -> showRenameDialog(group));

        MenuItem deleteItem = new MenuItem("删除");
        if (isDefaultGroup) {
            deleteItem.setDisable(true);
        } else if (group.getFileCount() > 0) {
            deleteItem.setDisable(true);
        }
        deleteItem.setOnAction(e -> showDeleteConfirm(group));

        menu.getItems().addAll(renameItem, deleteItem);
        return menu;
    }

    @FXML
    private void onAddGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("新建分组");
        dialog.setHeaderText(null);
        dialog.setContentText("分组名称：");
        dialog.getEditor().setPromptText("请输入分组名称");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                GroupDto dto = new GroupDto(name.trim());
                groupService.createGroup(dto);
                refreshGroupList();
                int idx = groupItems.size() - 1;
                groupListView.getSelectionModel().select(idx);
            } catch (BusinessException e) {
                showAlert("错误", e.getMessage());
            }
        });
    }

    private void showRenameDialog(GroupVo group) {
        TextInputDialog dialog = new TextInputDialog(group.getName());
        dialog.setTitle("重命名分组");
        dialog.setHeaderText(null);
        dialog.setContentText("分组名称：");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            try {
                GroupDto dto = new GroupDto(name.trim());
                groupService.renameGroup(group.getId(), dto);
                refreshGroupList();
            } catch (BusinessException e) {
                showAlert("错误", e.getMessage());
            }
        });
    }

    private void showDeleteConfirm(GroupVo group) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText(null);
        alert.setContentText("确定要删除分组\"" + group.getName() + "\"吗？\n此操作不可恢复。");

        ButtonType confirmButton = new ButtonType("确定删除", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == confirmButton) {
            try {
                groupService.deleteGroup(group.getId());
                refreshGroupList();
                if (!groupItems.isEmpty()) {
                    groupListView.getSelectionModel().select(0);
                }
            } catch (BusinessException e) {
                showAlert("错误", e.getMessage());
            }
        }
    }

    private void moveFileToGroup(String filePath, String targetGroupId) {
        try {
            groupService.moveFileToGroup(filePath, targetGroupId);
            refreshGroupList();
            if (currentSelectedGroupId != null) {
                loadFilesForGroup(currentSelectedGroupId);
            }
        } catch (BusinessException e) {
            showAlert("错误", e.getMessage());
        }
    }

    private void loadFilesForGroup(String groupId) {
        currentSelectedGroupId = groupId;
        CompletableFuture.supplyAsync(() -> groupService.refreshFiles(groupId))
                .thenAcceptAsync(files -> {
                    fileItems.setAll(files);
                    updateFilePanel(groupId, files.size());
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert("错误", "加载文件失败: " + ex.getMessage()));
                    return null;
                });
    }

    private void updateFilePanel(String groupId, int fileCount) {
        GroupVo currentGroup = groupItems.stream()
                .filter(g -> g.getId().equals(groupId))
                .findFirst().orElse(null);
        String groupName = currentGroup != null ? currentGroup.getName() : "未知";
        currentGroupLabel.setText("分组名：" + groupName);
        fileCountLabel.setText("共 " + fileCount + " 个文件");

        boolean isEmpty = fileCount == 0;
        emptyHintLabel.setVisible(isEmpty);
        fileListView.setVisible(!isEmpty);
    }

    public void refreshGroupList() {
        List<GroupVo> groups = groupService.listGroups();
        String previouslySelected = currentSelectedGroupId;
        groupItems.setAll(groups);

        if (previouslySelected != null) {
            for (int i = 0; i < groupItems.size(); i++) {
                if (groupItems.get(i).getId().equals(previouslySelected)) {
                    groupListView.getSelectionModel().select(i);
                    return;
                }
            }
        }
    }

    public void onNewFileDetected(DesktopFileVo file) {
        Platform.runLater(() -> {
            String selectedGroupId = currentSelectedGroupId;
            if (selectedGroupId == null && !groupItems.isEmpty()) {
                selectedGroupId = groupItems.get(0).getId();
            }
            if (selectedGroupId != null) {
                refreshGroupList();
                loadFilesForGroup(selectedGroupId);
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void executeFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                showAlert("错误", "文件不存在: " + filePath);
                return;
            }
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            log.error("执行文件失败: {}", filePath, e);
            showAlert("错误", "无法打开文件: " + e.getMessage());
        }
    }
}
