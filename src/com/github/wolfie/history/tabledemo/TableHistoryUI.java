package com.github.wolfie.history.tabledemo;

import javax.servlet.annotation.WebServlet;

import org.json.JSONException;
import org.json.JSONObject;

import com.github.wolfie.history.HistoryExtension;
import com.github.wolfie.history.HistoryExtension.PopStateEvent;
import com.github.wolfie.history.HistoryExtension.PopStateListener;
import com.github.wolfie.history.tabledemo.TableView.TableSelectionListener;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.Component;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.TabSheet.SelectedTabChangeEvent;
import com.vaadin.ui.TabSheet.SelectedTabChangeListener;
import com.vaadin.ui.UI;

@SuppressWarnings("serial")
public class TableHistoryUI extends UI {

    @WebServlet(urlPatterns = { "/TableDemo/*" }, asyncSupported = true)
    @VaadinServletConfiguration(productionMode = false, ui = TableHistoryUI.class)
    public static class Servlet extends VaadinServlet {
        // default implementation is fine.
    }

    protected static final String POJO_ID_KEY = "id";
    protected static final String VIEW_KEY = "view";

    public static final int TABLE_VIEW_DESELECTED = -1;

    private static final int TABLE_VIEW_STATE_VALUE = 1;
    private static final int ABOUT_VIEW_STATE_VALUE = 2;

    private MyPojo pojoToSerializeInState = null;
    private final HistoryExtension.ErrorListener historyErrorListener = new HistoryExtension.ErrorListener() {
        @Override
        public void onError(final HistoryExtension.ErrorEvent event) {
            Notification.show(
                    "Your browser doesn't seem to support pushState properly: "
                            + event.getErrorName(),
                    Notification.Type.ERROR_MESSAGE);
        }
    };
    private final PopStateListener popStateListener = new PopStateListener() {
        @Override
        public void popState(final PopStateEvent event) {
            applySerializedState(event.getStateAsJson());
        }
    };
    private final SelectedTabChangeListener tabChangeListener = new SelectedTabChangeListener() {
        @Override
        public void selectedTabChange(SelectedTabChangeEvent event) {
            if (applyingSerializedState) {
                /*
                 * the table's state is changing since we're reapplying the
                 * state. Ignore all calls during this time.
                 */
                return;
            }

            Component selectedTab = event.getTabSheet().getSelectedTab();

            String selection;
            if (selectedTab == tableView) {
                selection = "table";

                MyPojo selectedPojo = tableView.getSelected();
                if (selectedPojo != null) {
                    selection += "/" + selectedPojo.getId();
                }
                selection += "/";
            }

            else if (selectedTab == aboutView) {
                selection = "about/";
            }

            else {
                Notification.show("Unknown tab was selected",
                        Notification.Type.ERROR_MESSAGE);
                throw new IllegalStateException("Unknown tab was selected");
            }

            pushStateHelper(selection);
        }
    };

    private final TableSelectionListener tableSelectionListener = new TableSelectionListener() {
        @Override
        public void tableSelectionChanged(MyPojo selectedPojo) {
            if (applyingSerializedState) {
                /*
                 * the table's state is changing since we're reapplying the
                 * state. Ignore all calls during this time.
                 */
                return;
            }

            pojoToSerializeInState = selectedPojo;

            if (selectedPojo != null) {
                pushStateHelper("table/" + selectedPojo.getId() + "/");
            } else {
                pushStateHelper("table/");
            }
        }
    };

    private HistoryExtension history;

    private final AboutView aboutView = new AboutView();
    private final TableView tableView = new TableView(tableSelectionListener);
    private final TabSheet tabsheet = new TabSheet();

    private boolean applyingSerializedState = false;

    @Override
    protected void init(final VaadinRequest request) {
        tabsheet.setSizeFull();
        tabsheet.addTab(tableView, "Table View");
        tabsheet.addTab(aboutView, "About this Demo");
        tabsheet.addSelectedTabChangeListener(tabChangeListener);
        setContent(tabsheet);

        history = HistoryExtension.extend(this, popStateListener);
        history.addErrorListener(historyErrorListener);

        // initialize a starting state.
        history.replaceState(serializeState(), getPage().getLocation()
                .toString());
    }

    private void pushStateHelper(String nextUrl) {
        String targetUrl = "/History/TableDemo/" + nextUrl;

        String query = getPage().getLocation().getQuery();
        if (query != null) {
            targetUrl += "?" + query;
        }

        JSONObject state = serializeState();
        history.pushState(state, targetUrl);
    }

    private JSONObject serializeState() {
        try {
            final int view;
            if (tabsheet.getSelectedTab() == tableView)
                view = TABLE_VIEW_STATE_VALUE;
            else if (tabsheet.getSelectedTab() == aboutView)
                view = ABOUT_VIEW_STATE_VALUE;
            else {
                throw new IllegalStateException("unknown tab selected");
            }

            final int tableValueId;
            if (pojoToSerializeInState != null) {
                tableValueId = pojoToSerializeInState.getId();
            } else {
                tableValueId = TABLE_VIEW_DESELECTED;
            }

            JSONObject state = new JSONObject();
            state.put(VIEW_KEY, view);
            state.put(POJO_ID_KEY, tableValueId);
            return state;

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void applySerializedState(JSONObject state) {
        try {
            applyingSerializedState = true;

            int view = state.getInt(VIEW_KEY);
            int pojoId = state.getInt(POJO_ID_KEY);

            switch (view) {
            case TABLE_VIEW_STATE_VALUE:
                tabsheet.setSelectedTab(tableView);
                break;
            case ABOUT_VIEW_STATE_VALUE:
                tabsheet.setSelectedTab(aboutView);
                break;
            default:
                throw new UnsupportedOperationException("Unknown tab type: "
                        + view);
            }

            tableView.select(pojoId);
            pojoToSerializeInState = tableView.getSelected();

        } catch (JSONException e) {
            throw new RuntimeException(e);
        } finally {
            applyingSerializedState = false;
        }
    }
}