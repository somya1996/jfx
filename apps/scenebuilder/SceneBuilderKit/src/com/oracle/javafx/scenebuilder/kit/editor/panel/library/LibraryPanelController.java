/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates.
 * All rights reserved. Use is subject to license terms.
 *
 * This file is available and licensed under the following license:
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the distribution.
 *  - Neither the name of Oracle Corporation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.javafx.scenebuilder.kit.editor.panel.library;

import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.drag.source.AbstractDragSource;
import com.oracle.javafx.scenebuilder.kit.editor.drag.source.DocumentDragSource;
import com.oracle.javafx.scenebuilder.kit.editor.i18n.I18N;
import com.oracle.javafx.scenebuilder.kit.editor.panel.util.AbstractFxmlPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.util.dialog.AbstractModalDialog.ButtonID;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMDocument;
import com.oracle.javafx.scenebuilder.kit.library.Library;
import com.oracle.javafx.scenebuilder.kit.library.LibraryItem;
import com.oracle.javafx.scenebuilder.kit.library.LibraryItemNameComparator;
import com.oracle.javafx.scenebuilder.kit.library.user.UserLibrary;
import com.oracle.javafx.scenebuilder.kit.editor.panel.util.dialog.ErrorDialog;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMArchive;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMObject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;

/**
 * This class creates and controls the <b>Library Panel</b> of Scene Builder
 * Kit.
 *
 */
public class LibraryPanelController extends AbstractFxmlPanelController {

    private String searchPattern;
    ArrayList<LibraryItem> searchData = new ArrayList<>();
    private final String TEMP_FILE_EXTENSION = ".tmp"; //NOI18N
    // The name of the library section to keep opened. This is used when e.g.
    // a user jar file is imported to the library directory.
    // If the user was doing a search then we let the library layout unchanged.
    // If the user wasn't doing a search and custom content is updated then
    // we want to get Custom section opened.
    String sectionNameToKeepOpened = null;
    boolean initiateImportDialog = false;
    final List<File> jarAndFxmlFiles = new ArrayList<>();

    @FXML
    private Accordion libAccordion;
    @FXML
    Label noSearchResults;
    @FXML ListView<LibraryListItem> libSearchList;
    
    @FXML ListView<LibraryListItem> libList = new ListView<>();
    
    @FXML StackPane libPane;

    /*
     * Public
     */
    
    /**
     * Creates a library panel controller for the specified editor controller.
     *
     * @param c the editor controller (never null).
     */
    public LibraryPanelController(EditorController c) {
        super(LibraryPanelController.class.getResource("LibraryPanel.fxml"), I18N.getBundle(), c); //NOI18N
        startListeningToLibrary();
    }

    /**
     * Returns null or the search pattern applied to this library panel.
     *
     * @return null or the search pattern applied to this library panel.
     */
    public String getSearchPattern() {
        return searchPattern;
    }

    /**
     * Sets the search pattern to be applied to this library panel. When null
     * value is passed, the library panel displays all its items.
     *
     * @param searchPattern null or the search pattern to apply to this library
     * panel.
     */
    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern.toUpperCase(Locale.ENGLISH);
        searchPatternDidChange();
    }

    /**
     * @treatAsPrivate Perform the import jar action.
     */
    public void performImportJarFxml() {
        // Open file chooser and get user selection
        final List<File> importedFiles = performSelectJarOrFxmlFile();
        processImportJarFxml(importedFiles);
    }
    
    /**
     * @treatAsPrivate Perform the import of the selection
     * @param objects the FXOM objects to import to customize the Library content.
     */
    public void performImportSelection(List<FXOMObject> objects) {
        processInternalImport(objects);
    }
    
    /*
     * AbstractPanelController
     */
    
    /**
     * @treatAsPrivate FXOM document did change.
     * @param oldDocument the previous fxom document or null
     */
    @Override
    protected void fxomDocumentDidChange(FXOMDocument oldDocument) {
    }

    /**
     * @treatAsPrivate User scene graph did change.
     */
    @Override
    protected void sceneGraphRevisionDidChange() {
    }

    /**
     * @treatAsPrivate User scene graph did change.
     */
    @Override
    protected void cssRevisionDidChange() {
    }

    /**
     * @treatAsPrivate Job manager revision did change.
     */
    @Override
    protected void jobManagerRevisionDidChange() {
        // FXOMDocument has been modified by a job.
        // Library panel should probably not care for now.
    }
    
    /**
     * @treatAsPrivate Selection did change.
     */
    @Override
    protected void editorSelectionDidChange() {
    }

    /*
     * AbstractFxmlPanelController
     */
    
    /**
     * @treatAsPrivate Controller did load fxml.
     */
    @Override
    protected void controllerDidLoadFxml() {
        assert libAccordion != null;
        assert libPane != null;
        assert libList != null;
        assert noSearchResults != null;
        assert libSearchList != null;
        
        startListeningToDrop();
        setDisplayMode(DISPLAY_MODE.SECTIONS);
        populateLibraryPanel();
    }

    /*
     * Private
     */

    /**
     * The display mode of the Library panel is used to choose how items
     * are rendered within the panel.
     */
    public enum DISPLAY_MODE {

        SECTIONS {

                    @Override
                    public String toString() {
                        return I18N.getString("library.panel.menu.view.sections");
                    }
                },
        SEARCH,
        LIST {

                    @Override
                    public String toString() {
                        return I18N.getString("library.panel.menu.view.list");
                    }
                }
    };
    
    private DISPLAY_MODE currentDisplayMode;
    private DISPLAY_MODE previousDisplayMode = DISPLAY_MODE.SECTIONS;
    
    public void setPreviousDisplayMode(DISPLAY_MODE displayMode) {
        this.previousDisplayMode = displayMode;
    }
    
    public void setDisplayMode(DISPLAY_MODE displayMode) {
        this.currentDisplayMode = displayMode;
        
        switch (displayMode) {
            case SECTIONS:
                libAccordion.setVisible(true);
                libAccordion.setManaged(true);
                noSearchResults.setVisible(false);
                noSearchResults.setManaged(false);
                libSearchList.setVisible(false);
                libSearchList.setManaged(false);
                libList.setVisible(false);
                libList.setManaged(false);
                break;
            case SEARCH:
                libAccordion.setVisible(false);
                libAccordion.setManaged(false);
                if (libSearchList.getItems().isEmpty()) {
                    noSearchResults.setVisible(true);
                    noSearchResults.setManaged(true);
                    libSearchList.setVisible(false);
                    libSearchList.setManaged(false);
                } else {
                    noSearchResults.setVisible(false);
                    noSearchResults.setManaged(false);
                    libSearchList.setVisible(true);
                    libSearchList.setManaged(true);
                }
                libList.setVisible(false);
                libList.setManaged(false);
                break;
            case LIST:
                libAccordion.setVisible(false);
                libAccordion.setManaged(false);
                noSearchResults.setVisible(false);
                noSearchResults.setManaged(false);
                libSearchList.setVisible(false);
                libSearchList.setManaged(false);
                libList.setVisible(true);
                libList.setManaged(true);
                break;
            default:
                break;
        }
    }
    
    public DISPLAY_MODE getDisplayMode() {
        return currentDisplayMode;
    }
    
    private final ChangeListener<Library> libraryListener = new ChangeListener<Library>() {

        @Override
        public void changed(ObservableValue<? extends Library> ov, Library t, Library t1) {
            // When a jar is imported this listener is called two times.
            // First the UserLibrary is turned into BuiltinLibrary, then it is
            // turned back into a UserLibrary with the up to date library dir
            // content.
//            System.out.println("libraryListener called - t " + t + " - t1 " + t1);
            if (t instanceof UserLibrary) {
                t.getItems().removeListener(libraryItemListener);
                t.getItems().clear();
            }
            if (t1 instanceof UserLibrary) {
                t1.getItems().addListener(libraryItemListener);
                if (sectionNameToKeepOpened != null) {
                    sectionNameToKeepOpened = null;
                }
            }
            // libraryDidChange might not be called by several listeners.
            // Silencing the one below means I dunno how to get the selected index.
//            libraryDidChange(t);
        }
    };
    
    final ListChangeListener<LibraryItem> libraryItemListener = new ListChangeListener<LibraryItem>() {

            @Override
            public void onChanged(ListChangeListener.Change<? extends LibraryItem> change) {
//                System.out.println("libraryItemListener called");
                libraryDidChange(null);
            }
        };
    
    private void startListeningToLibrary() {
        getEditorController().libraryProperty().addListener(libraryListener);
    }
    
    // For now there is no scenario where this method might be of some use.
//    private void stopListeningToLibrary() {
//        getEditorController().libraryProperty().removeListener(libraryListener);
//    }

    void libraryDidChange(Library oldLib) {
        // Store selection context.
        if (libAccordion != null) {
            String sectionName = null;
            int selectedIndex = -1;
            if (sectionNameToKeepOpened != null) {
                sectionName = sectionNameToKeepOpened;
            }
            if (sectionName == null && libAccordion.getExpandedPane() != null) {
                sectionName = libAccordion.getExpandedPane().getText();
                // NOTE I dunno for now how to manage the selectedIndex.
                if (oldLib != null) {
                    final ListView<?> list = (ListView<?>)libAccordion.getExpandedPane().getContent();
                    LibraryItem item = ((LibraryItem)list.getSelectionModel().getSelectedItem());
                    selectedIndex = oldLib.getItems().indexOf(item);
//                    System.out.println("libraryDidChange - Set selected item index to " + selectedIndex);
                }
            }
            
//            System.out.println("libraryDidChange - Opened section name is " + sectionName);

            // Clear the content of the panel.
            libAccordion.getPanes().clear();

            // Reconstruct the panel content based on the new Library.
            populateLibraryPanel();

            // Restore selection context, if possible.
            setSelectedItem(selectedIndex, sectionName);
        }
    }
    
    // We need to discover the sections and the content of each one before being
    // able to populate the panel.
    // Each section is a TitledPane that contains a ListView of LibraryItem.
    // The order section are listed is managed by the comparator that comes with
    // the Library. In each section the LibraryItem are sorted with alphabetical
    // ordering.
    // First TitledPane is expanded.
    private void populateLibraryPanel() {
        // libData is backend structure for all that we put in the Accordion.
        LinkedHashMap<String, ArrayList<LibraryItem>> libData = new LinkedHashMap<>();
        TreeSet<String> sectionNames = new TreeSet<>(getEditorController().getLibrary().getSectionComparator());
        List<TitledPane> panes = libAccordion.getPanes();
        
        searchData.clear();
        libList.getItems().clear();
        
        if (getEditorController().getLibrary().getItems().size() > 0) {
            // Construct a sorted set of all lib section names.
            for (LibraryItem item : getEditorController().getLibrary().getItems()) {
                sectionNames.add(item.getSection());
            }

            // Create a sorted set of lib elements for each section.
            for (String sectionName : sectionNames) {
                libData.put(sectionName, new ArrayList<>());
            }

            // Add each LibraryItem to the appropriate set.
            for (LibraryItem item : getEditorController().getLibrary().getItems()) {
                libData.get(item.getSection()).add(item);
            }

            // Parse our lib data structure and populate the Accordion accordingly.
            for (String sectionName : sectionNames) {
                ListView<LibraryListItem> itemsList = new ListView<>();
                itemsList.setId(sectionName + "List"); // for QE //NOI18N
                itemsList.setCellFactory(cb);
                itemsList.addEventHandler(KeyEvent.KEY_RELEASED, keyEventHandler);
                Collections.sort(libData.get(sectionName), new LibraryItemNameComparator());
                for (LibraryItem item : libData.get(sectionName)) {
                    itemsList.getItems().add(new LibraryListItem(item));
                }
                TitledPane sectionPane = new TitledPane(sectionName, itemsList);
                sectionPane.setId(sectionName); // for QE
                sectionPane.setAnimated(true);
                panes.add(sectionPane);

                searchData.addAll(libData.get(sectionName));

                libList.getItems().add(new LibraryListItem(sectionName));
                for (LibraryItem item : libData.get(sectionName)) {
                    libList.getItems().add(new LibraryListItem(item));
                }
            }

            if (libAccordion.getPanes().size() >= 1) {
                libAccordion.setExpandedPane(panes.get(0));
            }

            if (libSearchList.getCellFactory() == null) {
                libSearchList.setCellFactory(cb);
            }

            if (libList.getCellFactory() == null) {
                libList.setCellFactory(cb);
            }

            libSearchList.addEventHandler(KeyEvent.KEY_RELEASED, keyEventHandler);
            libList.addEventHandler(KeyEvent.KEY_RELEASED, keyEventHandler);
        }
    }
    
    // We first try to open a section whose name matches the given one, then we
    // try to select within the ListView of the corresponding TitledPane a
    // LibraryItem with the given index.
    // If no matching elemnts are found we do nothing.
    private void setSelectedItem(Integer index, String sectionName) {
        if (sectionName != null) {
            // Try to open the section of the libAccordion that has the given name
            for (TitledPane tp : libAccordion.getPanes()) {
                if (tp.getText().equals(sectionName)) {
//                    System.out.println("setSelectedItem - Expand section " + sectionName);
                    libAccordion.setExpandedPane(tp);

                    if (index != -1) {
                        // Try to select the item
                        LibraryItem libItem = null;
                        try {
                            libItem = getEditorController().getLibrary().getItems().get(index);
                        } catch (IndexOutOfBoundsException ioobe) {
                            // No item with this index exists
//                            System.out.println("setSelectedItem - No item with index " + index + " exists");
                        }

                        if (libItem != null) {
//                            System.out.println("setSelectedItem - Scroll to then select the item " + libItem.getName());
                            final ListView<?> list = (ListView<?>) tp.getContent();
                            int indexInList = list.getItems().indexOf(libItem);
                            list.scrollTo(indexInList);
                            list.getSelectionModel().select(indexInList);
                        }
                    }
                }
            }
        }
    }
    
    private void searchPatternDidChange() {
        if (searchPattern == null || searchPattern.isEmpty()) {
            currentDisplayMode = previousDisplayMode;
        } else {
            if (currentDisplayMode != DISPLAY_MODE.SEARCH) {
                previousDisplayMode = currentDisplayMode;
                currentDisplayMode = DISPLAY_MODE.SEARCH;
            }
        }
        
        // The filtering is done by ignoring case, and by retaining any item that
        // contains the given pattern. An opened question is to filter as soon as
        // the pattern is two or more characters long: for now we react from the
        // first character.
        //
        // It can occur the whole Library is changed under the foots of SceneBuilder
        // while filtering is on going. searchData is never null so if its
        // content is changing then the filtering result will be inacurrate:
        // that is acceptable.
        //
        if (currentDisplayMode.equals(DISPLAY_MODE.SEARCH)) {
            libSearchList.getItems().clear();
            final ArrayList<LibraryItem> rawFilteredItem = new ArrayList<>();
            for (LibraryItem item : searchData) {
                if (item.getName().toUpperCase(Locale.ROOT).contains(searchPattern)) {
                    rawFilteredItem.add(item);
                }
            }
            Collections.sort(rawFilteredItem, new LibraryItemNameComparator());
            for (LibraryItem item : rawFilteredItem) {
                libSearchList.getItems().add(new LibraryListItem(item));
            }
            rawFilteredItem.clear();
        }
        
        setDisplayMode(currentDisplayMode);
    }
    
    // Key events listened onto the ListView
    // For some reason the listener when set on the cell (see LibraryListCell)
    // is never called, probably because it is the ListView which has the focus.
    private final EventHandler<KeyEvent> keyEventHandler = new EventHandler<KeyEvent>() {
        @Override
        public void handle(final KeyEvent e) {
            handleKeyEvent(e);
        }
    };
    
    private final Callback<ListView<LibraryListItem>, ListCell<LibraryListItem>> cb
            = new Callback<ListView<LibraryListItem>, ListCell<LibraryListItem>>() {
    
        @Override
        public ListCell<LibraryListItem> call(ListView<LibraryListItem> param) {
            return new LibraryListCell(getEditorController());
        }
    };
    
    private void handleKeyEvent(KeyEvent e) {
        // On ENTER we try to insert the item which is selected within the Library.
        if (e.getCode() == KeyCode.ENTER) {
            // This way of doing things requires the use of an @SuppressWarnings("unchecked")
//            final LibraryItem item = ((ListView<LibraryItem>)e.getSource()).getSelectionModel().getSelectedItem();
            // hence this other way below ...
            Object source = e.getSource();
            assert source instanceof ListView;
            final ListView<?> list = (ListView<?>)source;
            Object rawItem = list.getSelectionModel().getSelectedItem();
            assert rawItem instanceof LibraryListItem;
            final LibraryListItem listitem = (LibraryListItem)rawItem;
            final LibraryItem item = listitem.getLibItem();
             
            if (getEditorController().canPerformInsert(item)) {
                getEditorController().performInsert(item);
            }
            
            e.consume();
        }
    }
    
    private void startListeningToDrop() {
        libPane.setOnDragDropped(new EventHandler<DragEvent>() {

            @Override
            public void handle(DragEvent t) {
//                System.out.println("libPane onDragDropped");
                AbstractDragSource dragSource = getEditorController().getDragController().getDragSource();
                if (dragSource != null && dragSource instanceof DocumentDragSource) {
                    processInternalImport(((DocumentDragSource)dragSource).getDraggedObjects());
                } else {
                    initiateImportDialog = false;
                    jarAndFxmlFiles.clear();
                    t.setDropCompleted(true);
                    // Drop gesture is only valid when the Library is an instance of UserLibrary
                    if (getEditorController().getLibrary() instanceof UserLibrary) {
                        Dragboard db = t.getDragboard();
                        if (db.hasFiles()) {
                            final List<File> files = db.getFiles();
                            for (File file : files) {
                                // Keep only jar and fxml files
                                if (file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".fxml"))) { //NOI18N
//                                System.out.println("libPane onDragDropped - Retaining file " + file.getName());
                                    jarAndFxmlFiles.add(file);
                                }
                            }

                            // The import dialog might be kept opened by the user quite
                            // a long time.
                            // On Mac (not on Win), after around 20 seconds of opening
                            // time of the import dialog window the user sees a move from
                            // the lib panel to the Finder of the file icon, as if the drag
                            // is rejected.
                            // In order to silence (mask ?) this issue there's:
                            // - the delegation to setOnDragExited of the call of processImportJarFxml
                            // so that current handler returns fast.
                            // - the runLater in setOnDragExited, wrapped with a Timer set with a 1 second delay
                            // Is there a way to be notified when the import dialog
                            // can be run without interfering with the drag and drop sequence ?
                            initiateImportDialog = true;
                        }
                    }
                }
            }
        });
        
        libPane.setOnDragExited(new EventHandler<DragEvent>() {

            @Override
            public void handle(DragEvent t) {
//                System.out.println("libPane onDragExited");
                if (initiateImportDialog) {
                    initiateImportDialog = false;
                    final Timer timer = new Timer(true);
                    final TimerTask timerTask = new TimerTask() {

                        @Override
                        public void run() {
                            Platform.runLater(new Runnable() {

                                @Override
                                public void run() {
                                    processImportJarFxml(jarAndFxmlFiles);
                                }
                            });
                            // I don't need to use the timer later on so by
                            // cancelling it right here I'm sure free resources
                            // that otherwise would prevent the JVM from exiting.
                            timer.cancel();
                        }
                    };
                    timer.schedule(timerTask, 600); // milliseconds
                }
            }
        });
        
        
        libPane.setOnDragOver(new EventHandler<DragEvent>() {

            @Override
            public void handle(DragEvent t) {
//                System.out.println("libPane onDragOver");
                AbstractDragSource dragSource = getEditorController().getDragController().getDragSource();
                Dragboard db = t.getDragboard();
                // db has file when dragging a file from native file manager (Mac Finder, Windows Explorer, ...).
                if (db.hasFiles()) {
                    t.acceptTransferModes(TransferMode.COPY);
                } else if (dragSource != null) {
                    // dragSource is not null if the user drags something from Hierarchy or Content panel.
                    t.acceptTransferModes(TransferMode.MOVE);
                }
            }
        });
        
        // This one is called only if lib is the source of the drop.
        libPane.setOnDragDone(new EventHandler<DragEvent>() {

            @Override
            public void handle(DragEvent t) {
                assert getEditorController().getDragController().getDragSource() != null;
                getEditorController().getDragController().end();
                t.getDragboard().clear();
                t.consume();
            }
        });
        
    }
    
    // An internal import is an import to the Library initiated from within
    // SceneBuilder (from Content or Hierarchy).
    // We stop the watching thread to avoid potential parsing of a file that
    // would not yet be properly finalized on disk.
    private void processInternalImport(List<FXOMObject> objects) {
        String userLibraryPathString = ((UserLibrary) getEditorController().getLibrary()).getPath();
        Path libPath = Paths.get(userLibraryPathString);
        ((UserLibrary) getEditorController().getLibrary()).stopWatching();
        
        try {
            // The selection can be multiple, in which case each asset is processed
            // separately. The handling of dependencies will be addressed later on.
            for (FXOMObject asset : objects) {
                // Create an FXML layout as a String
                ArrayList<FXOMObject> selection = new ArrayList<>();
                selection.add(asset);
                final FXOMArchive fxomArchive = new FXOMArchive(selection);
                final FXOMArchive.Entry entry0 = fxomArchive.getEntries().get(0);
                String fxmlText = entry0.getFxmlText();

                // Write the FXML layout into a dedicated file stored in the Library dir
                // We use the class name of the top element and append a number:
                // if Library dir already contains SplitPane_1.fxml and top element is
                // of class SplitPane then we will create SplitPane_2.fxml and so on.
                String prefix = asset.getSceneGraphObject().getClass().getSimpleName();
                File fxmlFile = getUniqueFxmlFileName(prefix, userLibraryPathString);
                writeFxmlFile(fxmlFile, fxmlText, libPath);
            }
        } finally {
            ((UserLibrary) getEditorController().getLibrary()).startWatching();
        }
    }

    private void writeFxmlFile(File targetFile, String text, Path libPath) {
        Path targetFilePath = Paths.get(targetFile.getPath());
        createUserLibraryDir(libPath);

        try {
            // Create the new file
            Files.createFile(targetFilePath);

            // Write content of file
            try (PrintWriter writer = new PrintWriter(targetFile, "UTF-8")) { //NOI18N
                writer.write(text);
            }
            
            getEditorController().getMessageLog().logInfoMessage("log.info.file.creation.confirmation",
                    I18N.getBundle(), targetFilePath.getFileName().toString());
        } catch (IOException ioe) {
            final ErrorDialog errorDialog = new ErrorDialog(null);
            errorDialog.setTitle(I18N.getString("error.file.create.title"));
            errorDialog.setMessage(I18N.getString("error.file.create.message", targetFilePath.normalize().toString()));
            errorDialog.setDetails(I18N.getString("error.write.details"));
            errorDialog.setDebugInfoWithThrowable(ioe);
            errorDialog.showAndWait();
        }
    }
    
    private File getUniqueFxmlFileName(String prefix, String libDir) {
        int suffix = 0;
        File file = null;
        while (file == null || file.exists()) {
            suffix++;
            file = new File(libDir + File.separator + prefix + "_" + suffix + ".fxml"); //NOI18N
        }
        
        return file;
    }
    
    private void processImportJarFxml(List<File> importedFiles) {
        if (importedFiles != null && !importedFiles.isEmpty()) {
            Path libPath = Paths.get(((UserLibrary)getEditorController().getLibrary()).getPath());
            // Create UserLibrary dir if missing
            if (createUserLibraryDir(libPath)) {
                final List<File> fxmlFiles = getSubsetOfFiles(".fxml", importedFiles); //NOI18N

                if (!fxmlFiles.isEmpty() && enoughFreeSpaceOnDisk(fxmlFiles)) {
                    copyFilesToUserLibraryDir(fxmlFiles);
                }

                final List<File> jarFiles = getSubsetOfFiles(".jar", importedFiles); //NOI18N
                // For jar files we delegate to the import dialog.
                if (!jarFiles.isEmpty() && enoughFreeSpaceOnDisk(jarFiles)) {
                    // From here we know we will initiate the import dialog.
                    // This is why we put application window on the front.
                    // From there the import dialog window, which is application modal,
                    // should come on top of it.
                    final Window window = getPanelRoot().getScene().getWindow();
                    if (window instanceof Stage) {
                        final Stage stage = (Stage) window;
                        stage.toFront();
                    }

                    final ImportWindowController iwc
                            = new ImportWindowController(this, jarFiles, window);
                    // See comment in OnDragDropped handle set in method startListeningToDrop.
                    ButtonID userChoice = iwc.showAndWait();

                    if (userChoice.equals(ButtonID.OK) && currentDisplayMode.equals(DISPLAY_MODE.SECTIONS)) {
                        sectionNameToKeepOpened = UserLibrary.TAG_USER_DEFINED;
                    } else {
                        sectionNameToKeepOpened = null;
                    }
                }
            }
        }
    }
    
    private List<File> getSubsetOfFiles(String pattern, List<File> files) {
        final List<File> res = new ArrayList<>();
        
        for (File file : files) {
            if (file.getName().endsWith(pattern)) {
                res.add(file);
            }
        }
        
        return res;
    }
    
    private boolean createUserLibraryDir(Path libPath) {
        boolean dirCreated = false;
        try {
            // Files.createDirectories do nothing if provided Path already exists.
            Files.createDirectories(libPath, new FileAttribute<?>[]{});
            dirCreated = true;
        } catch (IOException ioe) {
            final ErrorDialog errorDialog = new ErrorDialog(null);
            errorDialog.setTitle(I18N.getString("error.dir.create.title"));
            errorDialog.setMessage(I18N.getString("error.dir.create.message", libPath.normalize().toString()));
            errorDialog.setDetails(I18N.getString("error.write.details"));
            errorDialog.setDebugInfoWithThrowable(ioe);
            errorDialog.showAndWait();
        }
        
        return dirCreated;
    }
    
    private boolean enoughFreeSpaceOnDisk(List<File> files) {
        long totalSize = Long.MAX_VALUE; // bytes
        try {
            for (File file : files) {
                Path targetPath = Paths.get(file.getAbsolutePath());
                totalSize += Files.size(targetPath);
            }
        } catch (IOException ioe) {
            final ErrorDialog errorDialog = new ErrorDialog(null);
            errorDialog.setTitle(I18N.getString("error.disk.space.title"));
            errorDialog.setMessage(I18N.getString("error.disk.space.message"));
            errorDialog.setDetails(I18N.getString("error.write.details"));
            errorDialog.setDebugInfoWithThrowable(ioe);
            errorDialog.showAndWait();
        }
        
        final File libFile = new File(((UserLibrary)getEditorController().getLibrary()).getPath());
        return totalSize < libFile.getFreeSpace();
    }
    
    // Each copy is done via an intermediate temporary file that is renamed if
    // the copy goes well (for atomicity). If a copy fails we try to erase the
    // temporary file to stick to an as clean as possible disk content.
    // TODO fix DTL-5879 [When copying FXML files in lib dir we have to copy files which are external references as well]
    void copyFilesToUserLibraryDir(List<File> files) {
        int errorCount = 0;
        IOException savedIOE = null;
        String savedFileName = ""; //NOI18N
        Path tempTargetPath = null;
        String userLibraryPath = ((UserLibrary) getEditorController().getLibrary()).getPath();
        // Here we deactivate the UserLib so that it unlocks the files contained
        // in the lib dir in the file system meaning (especially on Windows).
        ((UserLibrary) getEditorController().getLibrary()).stopWatching();
        
        try {
            for (File file : files) {
                savedFileName = file.getName();
                tempTargetPath = Paths.get(userLibraryPath, file.getName() + TEMP_FILE_EXTENSION);
                Path ultimateTargetPath = Paths.get(userLibraryPath, file.getName());
                Files.deleteIfExists(tempTargetPath);
                Files.copy(file.toPath(), tempTargetPath, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tempTargetPath, ultimateTargetPath, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (IOException ioe) {
            errorCount++;
            savedIOE = ioe;
        } finally {
            if (tempTargetPath != null) {
                try {
                    Files.deleteIfExists(tempTargetPath);
                } catch (IOException ioe) {
                    errorCount++;
                    savedIOE = ioe;
                }
            }
        }

        ((UserLibrary) getEditorController().getLibrary()).startWatching();

        if (errorCount > 0) {
            final ErrorDialog errorDialog = new ErrorDialog(null);
            errorDialog.setTitle(I18N.getString("error.copy.title"));
            if (errorCount == 1) {
                errorDialog.setMessage(I18N.getString("error.copy.message.single", savedFileName, userLibraryPath));
                errorDialog.setDebugInfoWithThrowable(savedIOE);
            } else {
                errorDialog.setMessage(I18N.getString("error.copy.message.multiple", errorCount, userLibraryPath));
            }
            errorDialog.setDetails(I18N.getString("error.write.details"));
            errorDialog.showAndWait();
        }
    }
    
    /**
     * Open a file chooser that allows to select one or more FXML and JAR file.
     * @return the list of selected files
     */
    private List<File> performSelectJarOrFxmlFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18N.getString("lib.filechooser.filter.msg"),
                "*.fxml", "*.jar")); //NOI18N
        return fileChooser.showOpenMultipleDialog(null);
    }
}