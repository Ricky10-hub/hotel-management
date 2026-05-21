import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javafx.scene.control.ScrollPane;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

public class Main extends Application {

    // ===================== DATABASE =====================
    private static final String DB_URL = "jdbc:sqlite:grand_heritage.db";
    private Connection conn;

    private void initDB() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(DB_URL);
            conn.setAutoCommit(true);
            Statement st = conn.createStatement();

            // Create tables if they don't exist
            st.execute(
                "CREATE TABLE IF NOT EXISTS rooms (" +
                "    room_no    INTEGER PRIMARY KEY," +
                "    room_type  TEXT    NOT NULL," +
                "    base_price REAL    NOT NULL," +
                "    floor_no   INTEGER NOT NULL DEFAULT 1," +
                "    occupied   INTEGER NOT NULL DEFAULT 0" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS active_guests (" +
                "    room_no      INTEGER PRIMARY KEY," +
                "    name         TEXT    NOT NULL," +
                "    contact      TEXT    NOT NULL," +
                "    id_proof     TEXT    NOT NULL DEFAULT 'Aadhaar Card'," +
                "    days         INTEGER NOT NULL," +
                "    bill         REAL    NOT NULL," +
                "    wifi         INTEGER NOT NULL DEFAULT 0," +
                "    breakfast    INTEGER NOT NULL DEFAULT 0," +
                "    parking      INTEGER NOT NULL DEFAULT 0," +
                "    laundry      INTEGER NOT NULL DEFAULT 0," +
                "    paid         INTEGER NOT NULL DEFAULT 0" +
                ")"
            );

            st.execute(
                "CREATE TABLE IF NOT EXISTS guest_history (" +
                "    id           INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    room_no      INTEGER NOT NULL," +
                "    name         TEXT    NOT NULL," +
                "    contact      TEXT    NOT NULL," +
                "    id_proof     TEXT    NOT NULL DEFAULT 'Aadhaar Card'," +
                "    days         INTEGER NOT NULL," +
                "    bill         REAL    NOT NULL," +
                "    wifi         INTEGER NOT NULL DEFAULT 0," +
                "    breakfast    INTEGER NOT NULL DEFAULT 0," +
                "    parking      INTEGER NOT NULL DEFAULT 0," +
                "    laundry      INTEGER NOT NULL DEFAULT 0," +
                "    status       TEXT    NOT NULL DEFAULT 'CHECKED OUT'," +
                "    checkin_time TEXT    DEFAULT (datetime('now','localtime'))" +
                ")"
            );

            // Migrations: safely add missing columns to existing older databases
            migrateAddColumn(st, "rooms",         "floor_no",    "INTEGER NOT NULL DEFAULT 1");
            migrateAddColumn(st, "active_guests", "id_proof",    "TEXT DEFAULT 'Aadhaar Card'");
            migrateAddColumn(st, "active_guests", "parking",     "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumn(st, "active_guests", "laundry",     "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumn(st, "active_guests", "room_type",   "TEXT DEFAULT 'STANDARD'");
            migrateAddColumn(st, "guest_history", "id_proof",    "TEXT DEFAULT 'Aadhaar Card'");
            migrateAddColumn(st, "guest_history", "parking",     "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumn(st, "guest_history", "laundry",     "INTEGER NOT NULL DEFAULT 0");
            migrateAddColumn(st, "guest_history", "room_type",   "TEXT DEFAULT 'STANDARD'");
            migrateAddColumn(st, "guest_history", "checkin_time","TEXT DEFAULT (datetime('now','localtime'))");

            st.close();
        } catch (Exception e) {
            showBootError("Database init failed: " + e.getMessage());
        }
    }

    /** Adds a column only if it does not already exist (safe migration). */
    private void migrateAddColumn(Statement st, String table, String column, String definition) {
        try {
            boolean exists = false;
            try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    if (column.equals(rs.getString("name"))) { exists = true; break; }
                }
            }
            if (!exists) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        } catch (Exception ignored) { /* non-fatal: skip if migration fails */ }
    }

    private void showBootError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle("DB Error"); a.setHeaderText(null); a.showAndWait();
        System.exit(1);
    }

    // ===================== APP STATE =====================
    ArrayList<RoomModel>             rooms        = new ArrayList<>();
    HashMap<Integer, CustomerModel>  activeGuests = new HashMap<>();
    ArrayList<CustomerModel>         guestHistory = new ArrayList<>();
    ArrayList<String[]>              activityLog  = new ArrayList<>();

    ObservableList<RoomRow>         roomTableData  = FXCollections.observableArrayList();
    ObservableList<RoomRow>         availRoomsData = FXCollections.observableArrayList();
    ObservableList<OccupiedRow>     occupiedData   = FXCollections.observableArrayList();
    ObservableList<GuestHistoryRow> histData       = FXCollections.observableArrayList();

    TableView<RoomRow>         availRoomsTable = new TableView<>(availRoomsData);
    TableView<OccupiedRow>     occupiedTable   = new TableView<>(occupiedData);
    TextArea receiptArea = new TextArea();

    // Dashboard live labels
    Label dashTotalVal   = new Label("0");
    Label dashAvailVal   = new Label("0");
    Label dashBookedVal  = new Label("0");
    Label dashRevenueVal = new Label("Rs.0");
    Label dashOccPctVal  = new Label("0%");
    Label clockLabel     = new Label();
    VBox activityBox     = new VBox(6);
    VBox occBreakdownBox = new VBox(12);

    // Reports live labels
    Label rptTotalRevVal  = new Label("Rs.0");
    Label rptAvgStayVal   = new Label("0 days");
    Label rptGuestCntVal  = new Label("0");
    Label rptRevParVal    = new Label("Rs.0");
    TableView<String[]>      revTypeTable = new TableView<>();
    ObservableList<String[]> revTypeData  = FXCollections.observableArrayList();
    VBox addonBarBox = new VBox(12);

    // ===================== COUNTRY PHONE RULES =====================
    static final String[][] COUNTRIES = {
        {"India (+91)",        "+91",  "10"},
        {"USA (+1)",           "+1",   "10"},
        {"UK (+44)",           "+44",  "10"},
        {"Australia (+61)",    "+61",  "9" },
        {"Canada (+1)",        "+1",   "10"},
        {"UAE (+971)",         "+971", "9" },
        {"Singapore (+65)",    "+65",  "8" },
        {"Germany (+49)",      "+49",  "10"},
        {"France (+33)",       "+33",  "9" },
        {"Japan (+81)",        "+81",  "10"},
        {"China (+86)",        "+86",  "11"},
        {"Brazil (+55)",       "+55",  "11"},
        {"South Africa (+27)", "+27",  "9" },
        {"Nepal (+977)",       "+977", "10"},
        {"Sri Lanka (+94)",    "+94",  "9" },
    };
    private int getRequiredDigits(String lbl) {
        for (String[] c : COUNTRIES) if (c[0].equals(lbl)) return Integer.parseInt(c[2]);
        return 10;
    }
    private String getDialCode(String lbl) {
        for (String[] c : COUNTRIES) if (c[0].equals(lbl)) return c[1];
        return "+91";
    }

    // ===================== COLOUR PALETTE (DARK MODE) =====================
    static final String NAVY       = "#0f172a";
    static final String NAVY2      = "#1e293b";
    static final String NAVY3      = "#334155";
    static final String GOLD       = "#38bdf8";
    static final String GOLD2      = "#0ea5e9";
    static final String CREAM      = "#0f172a";
    static final String CREAM2     = "#1e293b";
    static final String CREAM3     = "#334155";
    static final String SLATE      = "#cbd5e1";
    static final String SLATE2     = "#94a3b8";
    static final String SUCCESS_BG = "#064e3b";
    static final String SUCCESS_FG = "#34d399";
    static final String DANGER_BG  = "#7f1d1d";
    static final String DANGER_FG  = "#f87171";
    static final String WARN_BG    = "#78350f";
    static final String WARN_FG    = "#fbbf24";
    static final String INFO_BG    = "#1e3a8a";
    static final String INFO_FG    = "#60a5fa";
    static final String PURPLE_FG  = "#a78bfa";
    static final String WHITE      = "#f8fafc";

    // ===================== STYLE BUILDERS =====================
    private String bg(String c)   { return "-fx-background-color:" + c + ";"; }
    private String fg(String c)   { return "-fx-text-fill:" + c + ";"; }
    private String rad(int r)     { return "-fx-background-radius:" + r + "px;"; }
    private String brad(int r)    { return "-fx-border-radius:" + r + "px;"; }
    private String pad(int v, int h){ return "-fx-padding:" + v + " " + h + ";"; }
    private String font(int sz)   { return "-fx-font-size:" + sz + ";"; }
    private String fontB(int sz)  { return font(sz) + "-fx-font-weight:bold;"; }

    static final String FF     = "-fx-font-family:'Segoe UI';";
    static final String FIELD  = "-fx-background-color:#1e293b;-fx-border-color:#334155;-fx-text-fill:#f8fafc;" +
                                  "-fx-border-radius:6px;-fx-background-radius:6px;-fx-padding:8 12;-fx-font-size:13;-fx-font-family:'Segoe UI';";
    static final String TBL    = "-fx-font-family:'Segoe UI';-fx-font-size:12;-fx-background-color:#0f172a;" +
                                  "-fx-table-cell-border-color:#1e293b;-fx-control-inner-background:#0f172a;-fx-text-fill:#f8fafc;";
    static final String CARD_S = "-fx-background-color:#1e293b;-fx-background-radius:10px;" +
                                  "-fx-border-color:#334155;-fx-border-radius:10px;-fx-border-width:1;" +
                                  "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.25),10,0,0,4);";
    static final String MUTED  = "-fx-font-size:11;-fx-text-fill:#94a3b8;-fx-font-family:'Segoe UI';";

    // ===================== START =====================
    @Override
    public void start(Stage stage) {
        initDB();
        startClock();
        showLogin(stage);
    }

    @Override
    public void stop() {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
    }

    private void startClock() {
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            clockLabel.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        }));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
    }

    // ===================== LOGIN =====================
    private void showLogin(Stage stage) {
        VBox outer = new VBox();
        outer.setAlignment(Pos.CENTER);
        outer.setStyle("-fx-background-color: linear-gradient(to bottom right, #0f172a 0%, #1e3a5f 50%, #0f172a 100%);");

        Label logoIcon = new Label("GH");
        logoIcon.setStyle(fontB(24) + fg("#0f172a") + "-fx-background-color: linear-gradient(to bottom right, #38bdf8, #0ea5e9);" +
                         rad(50) + pad(16, 20) + FF);

        Label hotelName = new Label("GRAND HERITAGE");
        hotelName.setStyle(fontB(20) + fg(WHITE) + FF + "-fx-letter-spacing:3;");
        Label hotelSub = new Label("Hotel Management System");
        hotelSub.setStyle(font(12) + fg(SLATE) + FF);
        VBox logoBox = new VBox(6, logoIcon, hotelName, hotelSub);
        logoBox.setAlignment(Pos.CENTER);
        logoBox.setStyle(bg(NAVY2) + pad(28, 0) + brad(10) + rad(10) +
                         "-fx-border-color:" + NAVY3 + ";-fx-border-width:0 0 1 0;");

        Label userLbl = styledLabel("Username", 11, SLATE);
        TextField user = styledField("admin");
        Label passLbl = styledLabel("Password", 11, SLATE);
        PasswordField pass = new PasswordField();
        pass.setStyle(FIELD);
        pass.setPrefHeight(38);

        Button loginBtn = new Button("Sign In");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(44);
        loginBtn.setStyle(bg(GOLD) + fg(NAVY) + fontB(14) + rad(8) + FF +
                          "-fx-cursor:hand;" + pad(0, 0));

        Label dbStatus = new Label("* Connected to SQLite: grand_heritage.db");
        dbStatus.setStyle(font(11) + fg("#16a34a") + FF);
        dbStatus.setAlignment(Pos.CENTER);

        Label err = new Label();
        err.setStyle(font(12) + fg("#ef4444") + FF);

        VBox fields = new VBox(8,
            userLbl, user,
            passLbl, pass,
            loginBtn, err, dbStatus
        );
        fields.setPadding(new Insets(20, 28, 20, 28));

        VBox card = new VBox(0, logoBox, fields);
        card.setMaxWidth(340);
        card.setStyle(bg(NAVY2) + rad(12) + brad(12) +
                      "-fx-border-color:" + NAVY3 + ";-fx-border-width:1;" +
                      "-fx-effect:dropshadow(gaussian,rgba(56,189,248,0.15),30,0,0,8);");

        loginBtn.setOnAction(e -> {
            if (user.getText().equals("admin") && pass.getText().equals("1234")) {
                showMain(stage);
            } else {
                err.setText("Invalid credentials. Use admin / 1234");
            }
        });
        pass.setOnAction(e -> loginBtn.fire());

        outer.getChildren().add(card);
        stage.setTitle("Grand Heritage -- Login");
        stage.setScene(new Scene(outer, 1100, 720));
        stage.show();
    }

    // ===================== MAIN WINDOW =====================
    private void showMain(Stage stage) {
        loadAll();

        Label logoMark = new Label("GH");
        logoMark.setStyle(fontB(13) + fg(NAVY) + bg(GOLD2) +
                          rad(50) + pad(9, 12) + FF);

        VBox logoText = new VBox(1);
        Label hotelBig = new Label("GRAND HERITAGE");
        hotelBig.setStyle(fontB(15) + fg("white") + FF + "-fx-letter-spacing:1.5;");
        Label hotelSml = new Label("Management System");
        hotelSml.setStyle(font(10) + fg(SLATE2) + FF);
        logoText.getChildren().addAll(hotelBig, hotelSml);

        clockLabel.setStyle(font(12) + fg(SLATE2) + FF + "-fx-font-family:monospace;");

        Label dbChip  = badge("* SQLite", SUCCESS_FG, SUCCESS_BG);
        Label verChip = badge("v2.1", GOLD, NAVY3);

        Button logoutBtn = new Button("Sign Out");
        logoutBtn.setStyle(bg(DANGER_BG) + fg(DANGER_FG) + fontB(12) +
                           rad(6) + brad(6) + pad(7, 16) + FF + "-fx-cursor:hand;");
        logoutBtn.setOnAction(e -> showLogin(stage));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(12, logoMark, logoText, spacer, verChip, dbChip, clockLabel, logoutBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: linear-gradient(to right, #0f172a, #1e293b);" +
                        pad(0, 20) + "-fx-min-height:60;-fx-max-height:60;" +
                        "-fx-border-color: linear-gradient(to right, #38bdf8, #0ea5e9);-fx-border-width:0 0 2 0;");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle(bg(CREAM) + "-fx-tab-min-width:130;-fx-tab-max-height:34;" +
                      "-fx-open-tab-animation:NONE;-fx-close-tab-animation:NONE;");

        Tab t0 = styledTab("Dashboard");
        Tab t1 = styledTab("Room Management");
        Tab t2 = styledTab("Billing & Reception");
        Tab t3 = styledTab("Guest History");
        Tab t4 = styledTab("Reports");

        t0.setContent(buildDashboardTab());
        t1.setContent(buildRoomTab());
        t2.setContent(buildBillingTab());
        t3.setContent(buildHistoryTab());
        t4.setContent(buildReportsTab());

        tabs.getTabs().addAll(t0, t1, t2, t3, t4);

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(tabs);
        root.setStyle(bg(CREAM));

        stage.setTitle("Grand Heritage -- Hotel Management");
        Scene scene = new Scene(root, 1200, 760);
        scene.getStylesheets().add("data:text/css," +
            "*{ -fx-text-fill: %23f8fafc; } " +
            ".table-view{ -fx-background-color: %230f172a; -fx-control-inner-background: %230f172a; } " +
            ".table-view .column-header-background{ -fx-background-color: %231e293b; } " +
            ".table-view .column-header{ -fx-background-color: %231e293b; } " +
            ".table-row-cell{ -fx-background-color: %230f172a; } " +
            ".table-row-cell:odd{ -fx-background-color: %231e293b; } " +
            ".table-row-cell:selected{ -fx-background-color: %2338bdf8; } " +
            ".tab-pane .tab-header-area .tab-header-background{ -fx-background-color: %230f172a; } " +
            ".tab-pane .tab{ -fx-background-color: %231e293b; } " +
            ".tab-pane .tab:selected{ -fx-background-color: %2338bdf8; } " +
            ".scroll-pane{ -fx-background-color: %230f172a; } " +
            ".scroll-pane .viewport{ -fx-background-color: %230f172a; } " +
            ".combo-box{ -fx-background-color: %231e293b; -fx-border-color: %23334155; -fx-border-radius: 6; -fx-background-radius: 6; } " +
            ".combo-box .list-cell{ -fx-text-fill: %23f8fafc; -fx-background-color: %231e293b; } " +
            ".combo-box .arrow-button{ -fx-background-color: %231e293b; } " +
            ".combo-box .arrow{ -fx-background-color: %23cbd5e1; } " +
            ".combo-box-popup .list-view{ -fx-background-color: %231e293b; -fx-control-inner-background: %231e293b; } " +
            ".combo-box-popup .list-cell{ -fx-text-fill: %23f8fafc; -fx-background-color: %231e293b; } " +
            ".combo-box-popup .list-cell:hover{ -fx-background-color: %2338bdf8; } " +
            ".tab-pane .tab{ -fx-background-color: %231e293b; -fx-background-radius: 6 6 0 0; -fx-padding: 8 20; } " +
            ".tab-pane .tab:selected{ -fx-background-color: %2338bdf8; } " +
            ".tab-pane .tab .tab-label{ -fx-text-fill: %23cbd5e1; -fx-font-weight: bold; } " +
            ".tab-pane .tab:selected .tab-label{ -fx-text-fill: %230f172a; } " +
            ".check-box .box{ -fx-background-color: %231e293b; -fx-border-color: %23334155; -fx-border-radius: 3; -fx-background-radius: 3; } " +
            ".check-box:selected .box{ -fx-background-color: %2338bdf8; -fx-border-color: %2338bdf8; } " +
            ".check-box .mark{ -fx-background-color: %23f8fafc; } " +
            ".separator .line{ -fx-border-color: %23334155; } "
        );
        stage.setScene(scene);
        stage.setResizable(true);
        refreshAll();
    }

    private Tab styledTab(String title) {
        Tab t = new Tab(title);
        return t;
    }

    // ===================== DASHBOARD =====================
    private VBox buildDashboardTab() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(22));
        box.setStyle(bg(CREAM));

        VBox pageHead = new VBox(2);
        Label title = new Label("Dashboard Overview");
        title.setStyle(fontB(20) + fg(NAVY) + FF);
        Label subTitle = new Label("Live occupancy, revenue and activity across Grand Heritage.");
        subTitle.setStyle(font(12) + fg(SLATE) + FF);
        pageHead.getChildren().addAll(title, subTitle);

        HBox statsRow = new HBox(12,
            statCard("Total Rooms",   dashTotalVal,   "#3b82f6", "All inventory"),
            statCard("Available",      dashAvailVal,   "#16a34a", "Ready for check-in"),
            statCard("Occupied",       dashBookedVal,  "#dc2626", "Active guests"),
            statCard("Occupancy Rate", dashOccPctVal,  "#d97706", "Utilisation %"),
            statCard("Live Revenue",   dashRevenueVal, GOLD,      "Current bookings")
        );
        statsRow.setFillHeight(true);
        for (javafx.scene.Node n : statsRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        HBox midRow = new HBox(12);

        // Occupancy breakdown card (LIVE)
        VBox occCard = new VBox(10);
        occCard.setStyle(CARD_S + pad(16, 16));
        HBox.setHgrow(occCard, Priority.ALWAYS);
        Label occTitle = new Label("Room Type Occupancy");
        occTitle.setStyle(fontB(13) + fg(WHITE) + FF);
        Label occSub = new Label("Live breakdown by category");
        occSub.setStyle(MUTED);
        occCard.getChildren().addAll(occTitle, occSub, occBreakdownBox);

        // Recent Activity card (LIVE)
        VBox actCard = new VBox(6);
        actCard.setStyle(CARD_S + pad(14, 16));
        HBox.setHgrow(actCard, Priority.ALWAYS);
        Label actTitle = new Label("Recent Activity");
        actTitle.setStyle(fontB(13) + fg(WHITE) + FF);
        Label actSub = new Label("Live feed of all operations");
        actSub.setStyle(MUTED);
        ScrollPane actScroll = new ScrollPane(activityBox);
        actScroll.setFitToWidth(true);
        actScroll.setPrefHeight(180);
        actScroll.setStyle(bg(NAVY2) + "-fx-border-width:0;");
        actCard.getChildren().addAll(actTitle, actSub, actScroll);

        midRow.getChildren().addAll(occCard, actCard);

        // Tip banner
        Label tip = new Label("All dashboard figures update live. Add rooms, check-in guests, and checkout to see real-time changes.");
        tip.setStyle(font(11) + fg(INFO_FG) + FF);
        tip.setWrapText(true);
        VBox tipBox = new VBox(tip);
        tipBox.setStyle(bg(INFO_BG) + rad(8) + brad(8) + pad(10, 14) +
                        "-fx-border-color:#93c5fd;-fx-border-width:1;");

        box.getChildren().addAll(pageHead, statsRow, midRow, tipBox);
        return box;
    }

    private VBox statCard(String title, Label valueLabel, String accentColor, String sub) {
        VBox card = new VBox(4);
        card.setStyle(CARD_S + pad(16, 18) +
                      "-fx-border-top-color:" + accentColor + ";-fx-border-width:3 0 0 0;");
        HBox.setHgrow(card, Priority.ALWAYS);
        Label t = new Label(title.toUpperCase());
        t.setStyle(font(10) + fg(SLATE) + FF + "-fx-letter-spacing:0.8;");
        valueLabel.setStyle(fontB(26) + fg(NAVY) + FF);
        Label s = new Label(sub);
        s.setStyle(MUTED);
        card.getChildren().addAll(t, valueLabel, s);
        Rectangle accent = new Rectangle();
        accent.setHeight(3);
        accent.setFill(Color.web(accentColor));
        return card;
    }

    private void updateDashboard() {
        int total  = rooms.size();
        int booked = activeGuests.size();
        int avail  = total - booked;
        double revenue = activeGuests.values().stream().mapToDouble(c -> c.bill).sum();
        int pct = total == 0 ? 0 : (int) Math.round((booked * 100.0) / total);
        dashTotalVal.setText(String.valueOf(total));
        dashAvailVal.setText(String.valueOf(avail));
        dashBookedVal.setText(String.valueOf(booked));
        dashOccPctVal.setText(pct + "%");
        dashRevenueVal.setText(String.format("Rs.%,.0f", revenue));
        updateOccBreakdown();
        updateActivityFeed();
    }

    private void updateOccBreakdown() {
        occBreakdownBox.getChildren().clear();
        String[][] types = {{"Suite","#fbbf24"},{"Deluxe","#60a5fa"},{"Standard","#34d399"}};
        for (String[] tp : types) {
            String typeName = tp[0];
            String color = tp[1];
            int totalT = 0, occT = 0;
            for (RoomModel r : rooms) {
                if (r.type.equalsIgnoreCase(typeName)) {
                    totalT++;
                    if (r.occupied) occT++;
                }
            }
            if (totalT == 0) continue;
            int pctT = (int) Math.round((occT * 100.0) / totalT);
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            Label lbl = new Label(typeName);
            lbl.setStyle(font(12) + fg(WHITE) + FF);
            Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
            Label info = new Label(occT + "/" + totalT + "  " + pctT + "%");
            info.setStyle(font(11) + fg(SLATE) + FF);
            row.getChildren().addAll(lbl, sp, info);
            Rectangle track = new Rectangle(180, 8);
            track.setArcWidth(4); track.setArcHeight(4);
            track.setFill(Color.web(NAVY3));
            Rectangle fill = new Rectangle(Math.max(2, pctT * 1.8), 8);
            fill.setArcWidth(4); fill.setArcHeight(4);
            fill.setFill(Color.web(color));
            StackPane bar = new StackPane(track, fill);
            bar.setAlignment(Pos.CENTER_LEFT);
            occBreakdownBox.getChildren().addAll(row, bar);
        }
    }

    private void updateActivityFeed() {
        activityBox.getChildren().clear();
        int limit = Math.min(activityLog.size(), 10);
        for (int i = activityLog.size() - 1; i >= activityLog.size() - limit && i >= 0; i--) {
            String[] act = activityLog.get(i);
            HBox aRow = new HBox(10);
            aRow.setPadding(new Insets(4, 0, 4, 0));
            Label dot = new Label("*");
            dot.setStyle(fontB(14) + fg(act[0]) + FF);
            VBox aText = new VBox(1);
            Label aTit = new Label(act[1]);
            aTit.setStyle(font(12) + fg(WHITE) + FF);
            Label aSub = new Label(act[2]);
            aSub.setStyle(MUTED);
            aText.getChildren().addAll(aTit, aSub);
            aRow.getChildren().addAll(dot, aText);
            activityBox.getChildren().add(aRow);
        }
        if (activityLog.isEmpty()) {
            Label empty = new Label("No activity yet. Add rooms or check in guests.");
            empty.setStyle(MUTED);
            activityBox.getChildren().add(empty);
        }
    }

    private void addActivity(String color, String message, String sub) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
        activityLog.add(new String[]{color, message, time + (sub.isEmpty() ? "" : " - " + sub)});
    }

    // ===================== ROOM MANAGEMENT =====================
    private VBox buildRoomTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setStyle(bg(CREAM));

        HBox hdr = new HBox(10);
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label pageTitle = new Label("Room Inventory");
        pageTitle.setStyle(fontB(18) + fg(WHITE) + FF);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        ComboBox<String> filterBox = new ComboBox<>();
        filterBox.getItems().addAll("All Types", "STANDARD", "DELUXE", "SUITE");
        filterBox.setValue("All Types");
        filterBox.setStyle(FF + font(13) + bg(NAVY2) + fg(WHITE) +
            "-fx-border-color:" + NAVY3 + ";-fx-border-radius:6;-fx-background-radius:6;");
        filterBox.setPrefWidth(150);
        filterBox.setOnAction(e -> {
            String sel = filterBox.getValue();
            refreshRoomTableFiltered(sel.equals("All Types") ? "" : sel);
        });

        TextField roomNoF = styledField("Room Number");
        roomNoF.setPrefWidth(130);

        ComboBox<String> floorBox = new ComboBox<>();
        floorBox.getItems().addAll("Floor 1","Floor 2","Floor 3","Floor 4","Floor 5");
        floorBox.setValue("Floor 1");
        floorBox.setStyle(FF + font(13) + bg(NAVY2) + fg(WHITE) +
            "-fx-border-color:" + NAVY3 + ";-fx-border-radius:6;-fx-background-radius:6;");
        floorBox.setPrefWidth(110);

        ComboBox<RoomType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(RoomType.values());
        typeBox.setValue(RoomType.DELUXE);
        typeBox.setStyle(FF + font(13) + bg(NAVY2) + fg(WHITE) +
            "-fx-border-color:" + NAVY3 + ";-fx-border-radius:6;-fx-background-radius:6;");
        typeBox.setPrefWidth(150);

        Button addBtn = colorBtn("+ Add Room", GOLD, NAVY);
        Button delBtn = colorBtn("Delete Room", DANGER_BG, DANGER_FG);

        addBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(roomNoF.getText().trim());
                for (RoomModel r : rooms)
                    if (r.number == id) { alert("Room " + id + " already exists."); return; }
                RoomType rt = typeBox.getValue();
                int floorNum = Integer.parseInt(floorBox.getValue().replace("Floor ","").trim());
                dbAddRoom(id, rt.name(), rt.getPrice(), floorNum);
                addActivity(SUCCESS_FG, "Room " + id + " added to inventory", rt.name() + " - Floor " + floorNum);
                loadAll(); refreshAll();
                roomNoF.clear();
            } catch (Exception ex) { alert("Enter a valid room number."); }
        });

        delBtn.setOnAction(e -> {
            try {
                int id = Integer.parseInt(roomNoF.getText().trim());
                if (activeGuests.containsKey(id)) {
                    alert("Cannot delete Room " + id + " -- guest is checked in. Checkout first."); return;
                }
                RoomModel rmDel = findRoom(id);
                String delType = rmDel != null ? rmDel.type : "";
                dbDeleteRoom(id);
                addActivity(DANGER_FG, "Room " + id + " removed from inventory", delType);
                loadAll(); refreshAll();
                roomNoF.clear();
            } catch (Exception ex) { alert("Enter a valid room number."); }
        });

        Label floorLbl = new Label("Floor:"); floorLbl.setStyle(fg(SLATE) + FF + font(12));
        Label typeLbl = new Label("Type:"); typeLbl.setStyle(fg(SLATE) + FF + font(12));
        Label filterLbl = new Label("Filter:"); filterLbl.setStyle(fg(SLATE) + FF + font(12));
        hdr.getChildren().addAll(pageTitle, sp,
                                  floorLbl, floorBox,
                                  typeLbl, typeBox,
                                  roomNoF, addBtn, delBtn,
                                  filterLbl, filterBox);

        HBox summRow = new HBox(12,
            roomTypeSummary("Standard","5 rooms","Rs.2,000 / night", NAVY2, "#34d399", NAVY3),
            roomTypeSummary("Deluxe",  "4 rooms","Rs.5,000 / night", NAVY2, "#60a5fa", NAVY3),
            roomTypeSummary("Suite",   "3 rooms","Rs.8,000 / night", NAVY2, "#fbbf24", NAVY3)
        );
        for (javafx.scene.Node n : summRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        TableView<RoomRow> invTable = new TableView<>(roomTableData);
        invTable.setStyle(TBL);
        invTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(invTable, Priority.ALWAYS);
        invTable.setPlaceholder(placeholder("No rooms added yet. Use the form above to add rooms."));
        invTable.getColumns().addAll(
            col("Room No",    "roomNo",    80),
            col("Floor",      "floor",     70),
            col("Room Type",  "type",      120),
            col("Base Price", "basePrice", 130),
            availCol()
        );

        VBox tableCard = new VBox(0, invTable);
        tableCard.setStyle(CARD_S);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        box.getChildren().addAll(hdr, summRow, tableCard);
        return box;
    }

    private HBox roomTypeSummary(String icon, String count, String price, String bgColor, String accentColor, String borderColor) {
        HBox card = new HBox(12);
        card.setStyle("-fx-background-color:" + bgColor + ";-fx-background-radius:10px;" +
                      "-fx-border-color:" + borderColor + ";-fx-border-radius:10px;-fx-border-width:1;" +
                      pad(14, 16) + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.15),6,0,0,2);");
        card.setAlignment(Pos.CENTER_LEFT);
        Label ico = new Label(icon);
        ico.setStyle(fontB(14) + "-fx-text-fill:" + accentColor + ";" + FF);
        VBox txt = new VBox(2);
        Label cnt = new Label(count);
        cnt.setStyle(fontB(14) + fg(WHITE) + FF);
        Label pr = new Label(price);
        pr.setStyle(font(11) + fg(SLATE) + FF);
        txt.getChildren().addAll(cnt, pr);
        card.getChildren().addAll(ico, txt);
        return card;
    }

    private void refreshRoomTableFiltered(String typeFilter) {
        roomTableData.clear();
        for (RoomModel r : rooms) {
            if (typeFilter.isEmpty() || r.type.equalsIgnoreCase(typeFilter)) {
                roomTableData.add(new RoomRow(r.number, r.type, r.basePrice, !r.occupied, r.floor));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private TableColumn<RoomRow, String> availCol() {
        TableColumn<RoomRow, String> c = new TableColumn<>("Status");
        c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAvailability()));
        c.setPrefWidth(120);
        c.setCellFactory(tc -> new TableCell<>() {
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.equals("AVAILABLE")) {
                    setStyle("-fx-text-fill:" + SUCCESS_FG + ";-fx-font-weight:bold;" +
                             "-fx-background-color:" + SUCCESS_BG + ";" + FF);
                } else {
                    setStyle("-fx-text-fill:" + DANGER_FG + ";-fx-font-weight:bold;" +
                             "-fx-background-color:" + DANGER_BG + ";" + FF);
                }
            }
        });
        return c;
    }

    // ===================== BILLING & RECEPTION =====================
    private HBox buildBillingTab() {
        HBox root = new HBox(14);
        root.setPadding(new Insets(18));
        root.setStyle(bg(CREAM));

        // LEFT: Room lists
        VBox left = new VBox(12);
        left.setPrefWidth(230);
        left.setMinWidth(210);

        Label avlTitle = sectionLabel("Available Rooms");
        availRoomsTable.setStyle(TBL);
        availRoomsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        availRoomsTable.setPrefHeight(220);
        availRoomsTable.setPlaceholder(placeholder("No rooms available."));
        if (availRoomsTable.getColumns().isEmpty())
            availRoomsTable.getColumns().addAll(
                col("No",   "roomNo", 55),
                col("Type", "type",   95)
            );

        Label occTitle2 = sectionLabel("Occupied Rooms");
        occupiedTable.setStyle(TBL);
        occupiedTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        occupiedTable.setPrefHeight(160);
        occupiedTable.setPlaceholder(placeholder("No occupied rooms."));
        if (occupiedTable.getColumns().isEmpty())
            occupiedTable.getColumns().addAll(
                col("No",   "roomNo",   45),
                col("Type", "roomType", 70)
            );

        VBox avlCard = new VBox(8, avlTitle, availRoomsTable);
        avlCard.setStyle(CARD_S + pad(12, 12));

        VBox occCard2 = new VBox(8, occTitle2, occupiedTable);
        occCard2.setStyle(CARD_S + pad(12, 12));
        left.getChildren().addAll(avlCard, occCard2);

        // MIDDLE: Booking form
        VBox mid = new VBox(10);
        mid.setPrefWidth(290);
        mid.setMinWidth(270);

        Label formTitle = new Label("New Booking");
        formTitle.setStyle(fontB(15) + fg(WHITE) + FF);

        TextField gName = styledField("Full Guest Name");

        // Country selector + phone
        ComboBox<String> countrySel = new ComboBox<>();
        for (String[] ct : COUNTRIES) countrySel.getItems().add(ct[0]);
        countrySel.setValue("India (+91)");
        countrySel.setStyle(FF + font(12) + bg(NAVY2) + fg(WHITE));
        countrySel.setPrefWidth(160);

        TextField gPhone = styledField("Phone digits only");
        gPhone.setPrefWidth(140);
        gPhone.setTextFormatter(new javafx.scene.control.TextFormatter<>(
            c -> c.getControlNewText().matches("\\d*") ? c : null));

        Label phoneHint = new Label("Enter 10 digits for India (+91)");
        phoneHint.setStyle(MUTED);
        countrySel.setOnAction(ev -> {
            int req = getRequiredDigits(countrySel.getValue());
            phoneHint.setText("Enter " + req + " digits for " + countrySel.getValue());
        });

        HBox phoneRow = new HBox(6, countrySel, gPhone);
        phoneRow.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> idProofSel = new ComboBox<>();
        idProofSel.getItems().addAll("Aadhaar Card","PAN Card","Passport","Driving License");
        idProofSel.setValue("Aadhaar Card");
        idProofSel.setStyle(FF + font(13) + bg(NAVY2) + fg(WHITE));
        idProofSel.setMaxWidth(Double.MAX_VALUE);

        TextField selRoomF = styledField("Click a room to select");
        selRoomF.setEditable(false);
        selRoomF.setStyle(FIELD + bg(CREAM2));

        TextField nDays = styledField("Number of Days");
        nDays.setTextFormatter(new javafx.scene.control.TextFormatter<>(
            new javafx.util.converter.IntegerStringConverter(), null,
            c -> c.getControlNewText().matches("\\d*") ? c : null));

        Label addonsLbl = new Label("Add-ons & Services");
        addonsLbl.setStyle(fontB(12) + fg(NAVY) + FF);
        CheckBox wifiCb  = styledCheck("High-Speed WiFi (+Rs.300)");
        CheckBox bfCb    = styledCheck("Breakfast Buffet (+Rs.300/day)");
        CheckBox parkCb  = styledCheck("Parking (+Rs.150/day)");
        CheckBox launCb  = styledCheck("Laundry Service (+Rs.200)");

        HBox addonsGrid = new HBox(10, new VBox(4, wifiCb, bfCb), new VBox(4, parkCb, launCb));
        VBox addonsCard = new VBox(8, addonsLbl, addonsGrid);
        addonsCard.setStyle(bg(CREAM2) + rad(8) + brad(8) + pad(10, 12));

        // Bill preview
        VBox billPreview = new VBox(5);
        billPreview.setStyle(bg(NAVY3) + rad(8) + brad(8) + pad(10, 12) +
                             "-fx-border-color:#475569;-fx-border-width:1;");
        billPreview.setVisible(false);
        billPreview.setManaged(false);
        Label billHeader = new Label("Estimated Bill");
        billHeader.setStyle(fontB(12) + fg(WHITE) + FF);
        Label billLines = new Label();
        billLines.setStyle(font(12) + fg(SLATE) + FF);
        billLines.setWrapText(true);
        Label billTotal = new Label();
        billTotal.setStyle(fontB(13) + fg(NAVY) + FF);
        Separator billSep = new Separator();
        billPreview.getChildren().addAll(billHeader, billLines, billSep, billTotal);

        Runnable calcBill = () -> {
            try {
                int rNo = Integer.parseInt(selRoomF.getText().trim());
                int d   = Integer.parseInt(nDays.getText().trim());
                RoomModel rm = findRoom(rNo);
                if (rm == null || d <= 0) { billPreview.setVisible(false); billPreview.setManaged(false); return; }
                boolean wifi = wifiCb.isSelected(), bf = bfCb.isSelected(),
                        park = parkCb.isSelected(), lau = launCb.isSelected();
                double roomCost = rm.basePrice * d;
                double wifiC = wifi ? 300 : 0;
                double bfC   = bf   ? 300.0 * d : 0;
                double parkC = park ? 150.0 * d : 0;
                double lauC  = lau  ? 200 : 0;
                double sub   = roomCost + wifiC + bfC + parkC + lauC;
                double gst   = sub * 0.18;
                double total = sub + gst;
                StringBuilder lines = new StringBuilder();
                lines.append(String.format("Room (%s x %d days): Rs.%,.0f%n", rm.type, d, roomCost));
                if (wifi) lines.append("WiFi: Rs.300\n");
                if (bf)   lines.append(String.format("Breakfast: Rs.%,.0f%n", bfC));
                if (park) lines.append(String.format("Parking: Rs.%,.0f%n", parkC));
                if (lau)  lines.append("Laundry: Rs.200\n");
                lines.append(String.format("GST (18%%): Rs.%,.0f", Math.round(gst)));
                billLines.setText(lines.toString());
                billTotal.setText(String.format("TOTAL: Rs.%,.0f", Math.round(total)));
                billPreview.setVisible(true); billPreview.setManaged(true);
            } catch (Exception ex) { billPreview.setVisible(false); billPreview.setManaged(false); }
        };
        nDays.textProperty().addListener((o, ov, nv) -> calcBill.run());
        wifiCb.selectedProperty().addListener((o, ov, nv) -> calcBill.run());
        bfCb.selectedProperty().addListener((o, ov, nv) -> calcBill.run());
        parkCb.selectedProperty().addListener((o, ov, nv) -> calcBill.run());
        launCb.selectedProperty().addListener((o, ov, nv) -> calcBill.run());

        availRoomsTable.setOnMouseClicked(e -> {
            RoomRow r = availRoomsTable.getSelectionModel().getSelectedItem();
            if (r != null) { selRoomF.setText(String.valueOf(r.getRoomNo())); calcBill.run(); }
        });
        occupiedTable.setOnMouseClicked(e -> {
            OccupiedRow r = occupiedTable.getSelectionModel().getSelectedItem();
            if (r != null) { selRoomF.setText(String.valueOf(r.getRoomNo())); calcBill.run(); }
        });

        Button invoiceBtn = colorBtn("Generate Invoice & Check In", GOLD, NAVY);
        invoiceBtn.setMaxWidth(Double.MAX_VALUE);
        invoiceBtn.setPrefHeight(42);

        Button checkoutBtn = new Button("Check-Out / Clear Room");
        checkoutBtn.setMaxWidth(Double.MAX_VALUE);
        checkoutBtn.setPrefHeight(42);
        checkoutBtn.setStyle(bg(WARN_BG) + fg(WARN_FG) + fontB(13) + rad(8) + brad(8) + FF +
                             "-fx-border-color:#fde68a;-fx-border-width:1;-fx-cursor:hand;");

        invoiceBtn.setOnAction(e -> {
            try {
                int rNo = Integer.parseInt(selRoomF.getText().trim());
                String nm = gName.getText().trim();
                String ph = gPhone.getText().trim();
                int d     = Integer.parseInt(nDays.getText().trim());
                String idp = idProofSel.getValue();
                if (nm.isEmpty() || ph.isEmpty()) { alert("Please fill in all guest details."); return; }
                int reqDig = getRequiredDigits(countrySel.getValue());
                if (ph.length() != reqDig) {
                    alert("Phone must be exactly " + reqDig + " digits for " + countrySel.getValue()); return;
                }
                String fullPhone = getDialCode(countrySel.getValue()) + ph;
                if (activeGuests.containsKey(rNo)) { alert("Room " + rNo + " is already booked!"); return; }
                RoomModel rm = findRoom(rNo);
                if (rm == null) { alert("Room not found."); return; }
                boolean wifi = wifiCb.isSelected(), bf = bfCb.isSelected(),
                        park = parkCb.isSelected(), lau = launCb.isSelected();
                double roomCost = rm.basePrice * d;
                double wifiC = wifi ? 300 : 0;
                double bfC   = bf   ? 300.0 * d : 0;
                double parkC = park ? 150.0 * d : 0;
                double lauC  = lau  ? 200 : 0;
                double sub   = roomCost + wifiC + bfC + parkC + lauC;
                double gst   = sub * 0.18;
                double total = sub + gst;
                dbBookRoom(rNo, nm, fullPhone, d, total, wifi, bf, park, lau, idp, rm.type);
                addActivity(SUCCESS_FG, nm + " checked into Room " + rNo, rm.type + " - " + d + " day(s)");
                loadAll(); refreshAll();
                receiptArea.setText(buildReceipt(nm, rNo, rm.type, d,
                                                  roomCost, wifiC, bfC, parkC, lauC, sub, gst, total, idp));
                gName.clear(); gPhone.clear(); nDays.clear();
                selRoomF.clear(); wifiCb.setSelected(false); bfCb.setSelected(false);
                parkCb.setSelected(false); launCb.setSelected(false);
                billPreview.setVisible(false); billPreview.setManaged(false);
                idProofSel.setValue("Aadhaar Card");
                countrySel.setValue("India (+91)");
            } catch (NumberFormatException ex) { alert("Enter a valid room number and number of days."); }
        });

        checkoutBtn.setOnAction(e -> {
            try {
                int rNo = Integer.parseInt(selRoomF.getText().trim());
                CustomerModel c = activeGuests.get(rNo);
                if (c == null) { alert("No active guest in Room " + rNo); return; }
                double dDays = c.days;
                double wifiC = c.wifi ? 300 : 0;
                double bfC = c.breakfast ? 300 * dDays : 0;
                double parkC = c.parking ? 150 * dDays : 0;
                double lauC = c.laundry ? 200 : 0;
                double subTotal = c.bill / 1.18;
                double roomCost = subTotal - (wifiC + bfC + parkC + lauC);
                double gst = c.bill - subTotal;
                String receiptText = buildReceipt(c.name, rNo, c.roomType, c.days,
                   roomCost, wifiC, bfC, parkC, lauC, subTotal, gst, c.bill, c.idProof);
                dbCheckout(rNo, c);
                addActivity(WARN_FG, c.name + " checked out from Room " + rNo, c.roomType + " - " + c.days + " day(s)");
                loadAll(); refreshAll();
                receiptArea.setText("====== CHECKOUT SUCCESSFUL ======\n\n" + receiptText);
                selRoomF.clear();
            } catch (Exception ex) { alert("Select an occupied room first."); }
        });

        VBox midCard = new VBox(8,
            formTitle,
            fieldWrap("Guest Full Name", gName),
            fieldWrap("Mobile Number", phoneRow),
            phoneHint,
            fieldWrap("ID Proof Type", idProofSel),
            fieldWrap("Selected Room", selRoomF),
            fieldWrap("Number of Days", nDays),
            addonsCard, billPreview,
            invoiceBtn, checkoutBtn);
        midCard.setStyle(CARD_S + pad(14, 14));
        VBox.setVgrow(midCard, Priority.ALWAYS);
        mid.getChildren().add(midCard);
        VBox.setVgrow(mid, Priority.ALWAYS);

        // RIGHT: Live Receipt
        VBox right = new VBox(10);
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox rcptHdr = new HBox(10);
        rcptHdr.setAlignment(Pos.CENTER_LEFT);
        Label rcptTitle = new Label("Live Receipt");
        rcptTitle.setStyle(fontB(14) + fg(NAVY) + FF);
        Region rcptSp = new Region(); HBox.setHgrow(rcptSp, Priority.ALWAYS);
        Button printBtn = new Button("Print / Export");
        printBtn.setStyle(bg(INFO_BG) + fg(INFO_FG) + fontB(12) + FF + rad(6) + brad(6) +
                          pad(6, 14) + "-fx-cursor:hand;");
        printBtn.setOnAction(ev -> {
            String rcpt = receiptArea.getText();
            if (rcpt == null || rcpt.trim().isEmpty()) { alert("No receipt to export."); return; }
            try {
                java.io.File f = new java.io.File("Receipt_" + System.currentTimeMillis() + ".txt");
                java.nio.file.Files.write(f.toPath(), rcpt.getBytes());
                alert("Receipt exported to:\n" + f.getAbsolutePath());
            } catch (Exception ex) { alert("Export failed: " + ex.getMessage()); }
        });
        Button clearBtn = new Button("Clear");
        clearBtn.setStyle(bg(CREAM2) + fg(SLATE) + font(12) + FF + rad(6) + brad(6) +
                          pad(6, 14) + "-fx-cursor:hand;");
        clearBtn.setOnAction(e -> receiptArea.clear());
        rcptHdr.getChildren().addAll(rcptTitle, rcptSp, clearBtn, printBtn);

        receiptArea.setEditable(false);
        receiptArea.setPromptText("Select a room and fill guest details to generate an invoice.");
        receiptArea.setStyle("-fx-font-family:Consolas,monospace;-fx-font-size:12;-fx-text-fill:#e2e8f0;" +
                             bg("#0f172a") + "-fx-border-color:" + NAVY3 + ";" +
                             "-fx-border-radius:8;-fx-background-radius:8;-fx-control-inner-background:#0f172a;");
        VBox.setVgrow(receiptArea, Priority.ALWAYS);

        VBox rcptCard = new VBox(10, rcptHdr, receiptArea);
        rcptCard.setStyle(CARD_S + pad(14, 14));
        VBox.setVgrow(rcptCard, Priority.ALWAYS);
        right.getChildren().add(rcptCard);
        VBox.setVgrow(right, Priority.ALWAYS);

        root.getChildren().addAll(left, mid, right);
        return root;
    }

    private String buildReceipt(String name, int rNo, String type, int days,
                                 double roomCost, double wifiC, double bfC,
                                 double parkC, double lauC,
                                 double sub, double gst, double total, String idProof) {
        String line = "==========================================";
        String dash = "------------------------------------------";
        StringBuilder sb = new StringBuilder();
        sb.append(line).append("\n");
        sb.append("       GRAND HERITAGE HOTEL\n");
        sb.append("         Management System\n");
        sb.append(line).append("\n\n");
        sb.append(String.format("Invoice Date: %s%n",
                  LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))));
        sb.append(String.format("Guest:        %s%n", name));
        sb.append(String.format("ID Proof:     %s%n", idProof));
        sb.append(String.format("Room:         %-5d (%s)%n", rNo, type));
        sb.append(String.format("Duration:     %-5d Day(s)%n%n", days));
        sb.append(String.format("%-26s Rs. %8.2f%n", "Room Rent (" + type + ")", roomCost));
        if (wifiC  > 0) sb.append(String.format("%-26s Rs. %8.2f%n", "High-Speed WiFi",  wifiC));
        if (bfC    > 0) sb.append(String.format("%-26s Rs. %8.2f%n", "Breakfast Buffet", bfC));
        if (parkC  > 0) sb.append(String.format("%-26s Rs. %8.2f%n", "Parking Service",  parkC));
        if (lauC   > 0) sb.append(String.format("%-26s Rs. %8.2f%n", "Laundry Service",  lauC));
        sb.append(dash).append("\n");
        sb.append(String.format("%-26s Rs. %8.2f%n", "Subtotal:",   sub));
        sb.append(String.format("%-26s Rs. %8.2f%n", "GST (18%):",  gst));
        sb.append(dash).append("\n");
        sb.append(String.format("%-26s Rs. %8.2f%n", "TOTAL AMOUNT:", total));
        sb.append(line).append("\n");
        sb.append("  Thank you for choosing Grand Heritage!\n");
        sb.append(line);
        return sb.toString();
    }

    // ===================== GUEST HISTORY =====================
    private VBox buildHistoryTab() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.setStyle(bg(CREAM));

        HBox hdr = new HBox(10);
        hdr.setAlignment(Pos.CENTER_LEFT);
        Label pageTitle = new Label("Complete Guest Records");
        pageTitle.setStyle(fontB(18) + fg(WHITE) + FF);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);

        TextField searchF = styledField("Search name or room...");
        searchF.setPrefWidth(200);

        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().addAll("All Status","CHECKED IN","CHECKED OUT");
        statusFilter.setValue("All Status");
        statusFilter.setStyle(FF + font(13) + bg(NAVY2) + fg(WHITE));
        statusFilter.setPrefWidth(140);

        Runnable doFilter = () -> {
            String q  = searchF.getText().toLowerCase();
            String st = statusFilter.getValue();
            histData.clear();
            for (CustomerModel c : guestHistory) {
                boolean matchQ = q.isEmpty() || c.name.toLowerCase().contains(q)
                                 || String.valueOf(c.roomNo).contains(q)
                                 || c.contact.contains(q);
                boolean matchS = st.equals("All Status") || c.status.equals(st);
                if (matchQ && matchS)
                    histData.add(new GuestHistoryRow(c.roomNo, c.name, c.contact, c.days, c.bill,
                                                     c.wifi, c.breakfast, c.parking, c.status, c.idProof, c.roomType));
            }
        };
        searchF.textProperty().addListener((o, ov, nv) -> doFilter.run());
        statusFilter.setOnAction(e -> doFilter.run());

        Button refreshBtn = colorBtn("Refresh", GOLD, NAVY);
        refreshBtn.setOnAction(e -> { loadAll(); refreshAll(); doFilter.run(); });

        hdr.getChildren().addAll(pageTitle, sp, searchF, statusFilter, refreshBtn);

        TableView<GuestHistoryRow> hist = new TableView<>(histData);
        hist.setStyle(TBL);
        hist.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(hist, Priority.ALWAYS);
        hist.setPlaceholder(placeholder("No guest records yet."));
        hist.getColumns().addAll(
            col("Room No",    "roomNo",    65),
            col("Type",       "roomType",  80),
            col("Guest Name", "name",     130),
            col("Phone",      "phone",    110),
            col("Days",       "days",      50),
            col("Bill (Rs.)", "bill",      90),
            col("WiFi",       "wifi",      55),
            col("Breakfast",  "breakfast", 70),
            col("Parking",    "parking",   65),
            col("ID Proof",   "idProof",  100),
            histStatusCol()
        );

        VBox tableCard = new VBox(0, hist);
        tableCard.setStyle(CARD_S);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        box.getChildren().addAll(hdr, tableCard);
        return box;
    }

    @SuppressWarnings("unchecked")
    private TableColumn<GuestHistoryRow, String> histStatusCol() {
        TableColumn<GuestHistoryRow, String> c = new TableColumn<>("Status");
        c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        c.setPrefWidth(110);
        c.setCellFactory(tc -> new TableCell<>() {
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                if (item.equals("CHECKED IN")) {
                    setStyle("-fx-text-fill:" + SUCCESS_FG + ";-fx-font-weight:bold;" +
                             "-fx-background-color:" + SUCCESS_BG + ";");
                } else {
                    setStyle("-fx-text-fill:" + WARN_FG + ";-fx-font-weight:bold;" +
                             "-fx-background-color:" + WARN_BG + ";");
                }
            }
        });
        return c;
    }

    // ===================== REPORTS TAB (LIVE) =====================
    private VBox buildReportsTab() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(20));
        box.setStyle(bg(CREAM));

        HBox pageHdr = new HBox(10);
        pageHdr.setAlignment(Pos.CENTER_LEFT);
        Label pageTitle = new Label("Analytics & Reports");
        pageTitle.setStyle(fontB(18) + fg(WHITE) + FF);
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Button exportBtn = colorBtn("Export Full Report", GOLD, NAVY);
        pageHdr.getChildren().addAll(pageTitle, sp, exportBtn);

        HBox kpiRow = new HBox(12,
            kpiCard("Total Revenue",      rptTotalRevVal,  GOLD),
            kpiCard("Avg Stay Duration",  rptAvgStayVal,   SUCCESS_FG),
            kpiCard("Total Guests",       rptGuestCntVal,  PURPLE_FG),
            kpiCard("RevPAR",             rptRevParVal,    INFO_FG)
        );
        for (javafx.scene.Node n : kpiRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // Revenue by room type table (live)
        VBox revCard = new VBox(10);
        revCard.setStyle(CARD_S + pad(14, 16));
        HBox.setHgrow(revCard, Priority.ALWAYS);
        Label revTitle = new Label("Revenue by Room Type");
        revTitle.setStyle(fontB(13) + fg(WHITE) + FF);
        revTypeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        revTypeTable.setStyle(TBL);
        revTypeTable.setFixedCellSize(36);
        revTypeTable.setPrefHeight(36 * 4 + 2);
        revTypeTable.setItems(revTypeData);
        if (revTypeTable.getColumns().isEmpty()) {
            String[] revCols = {"Room Type","Bookings","Revenue","Share"};
            for (int i = 0; i < revCols.length; i++) {
                final int fi = i;
                TableColumn<String[], String> tc = new TableColumn<>(revCols[i]);
                tc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[fi]));
                revTypeTable.getColumns().add(tc);
            }
        }
        revCard.getChildren().addAll(revTitle, revTypeTable);

        // Add-on revenue (live)
        VBox addonCard = new VBox(12);
        addonCard.setStyle(CARD_S + pad(14, 16));
        addonCard.setPrefWidth(320);
        Label addonTitle = new Label("Add-on Revenue Breakdown");
        addonTitle.setStyle(fontB(13) + fg(WHITE) + FF);
        addonCard.getChildren().addAll(addonTitle, addonBarBox);

        HBox midRow = new HBox(12, revCard, addonCard);
        VBox.setVgrow(revCard, Priority.ALWAYS);

        VBox noteBox = new VBox(4);
        noteBox.setStyle(bg(INFO_BG) + rad(8) + brad(8) + pad(12, 14) +
                         "-fx-border-color:#93c5fd;-fx-border-width:1;");
        Label noteTitle = new Label("Performance Insight");
        noteTitle.setStyle(fontB(12) + fg(INFO_FG) + FF);
        Label noteBody = new Label("All figures are computed live from your database. Check in guests and manage rooms to see real-time updates.");
        noteBody.setStyle(font(12) + fg(INFO_FG) + FF);
        noteBody.setWrapText(true);
        noteBox.getChildren().addAll(noteTitle, noteBody);

        box.getChildren().addAll(pageHdr, kpiRow, midRow, noteBox);
        return box;
    }

    private VBox kpiCard(String title, Label valueLabel, String accentColor) {
        VBox card = new VBox(4);
        card.setStyle(CARD_S + pad(14, 16));
        Label t = new Label(title.toUpperCase());
        t.setStyle(font(10) + fg(SLATE) + FF + "-fx-letter-spacing:0.8;");
        valueLabel.setStyle(fontB(22) + fg(WHITE) + FF);
        Label accent = new Label();
        accent.setStyle(font(1) + bg(accentColor) + "-fx-min-height:3;-fx-max-height:3;-fx-min-width:40;");
        card.getChildren().addAll(t, valueLabel, accent);
        return card;
    }

    private void updateReports() {
        // Combine active guests + history for stats
        ArrayList<CustomerModel> all = new ArrayList<>(guestHistory);
        for (CustomerModel c : activeGuests.values()) all.add(c);

        double totalRev = 0; int totalDays = 0; int guestCnt = all.size();
        HashMap<String, double[]> typeStats = new HashMap<>(); // type -> [bookings, revenue]
        double wifiRev = 0, bfRev = 0, parkRev = 0, lauRev = 0;

        for (CustomerModel c : all) {
            totalRev += c.bill;
            totalDays += c.days;
            String rt = (c.roomType != null && !c.roomType.isEmpty()) ? c.roomType : "STANDARD";
            typeStats.computeIfAbsent(rt, k -> new double[]{0, 0});
            typeStats.get(rt)[0]++;
            typeStats.get(rt)[1] += c.bill;
            if (c.wifi) wifiRev += 300;
            if (c.breakfast) bfRev += 300.0 * c.days;
            if (c.parking) parkRev += 150.0 * c.days;
            if (c.laundry) lauRev += 200;
        }

        rptTotalRevVal.setText(String.format("Rs.%,.0f", totalRev));
        rptAvgStayVal.setText(guestCnt == 0 ? "0 days" : String.format("%.1f days", totalDays / (double) guestCnt));
        rptGuestCntVal.setText(String.valueOf(guestCnt));
        int totalRooms = rooms.size();
        rptRevParVal.setText(totalRooms == 0 ? "Rs.0" : String.format("Rs.%,.0f", totalRev / totalRooms));

        // Revenue by type table
        revTypeData.clear();
        for (String type : new String[]{"SUITE", "DELUXE", "STANDARD"}) {
            double[] st = typeStats.getOrDefault(type, new double[]{0, 0});
            int bk = (int) st[0];
            double rv = st[1];
            String share = totalRev == 0 ? "0%" : String.format("%.0f%%", (rv / totalRev) * 100);
            revTypeData.add(new String[]{type, String.valueOf(bk), String.format("Rs.%,.0f", rv), share});
        }

        // Addon bars
        addonBarBox.getChildren().clear();
        double maxAddon = Math.max(1, Math.max(Math.max(wifiRev, bfRev), Math.max(parkRev, lauRev)));
        String[][] addonInfo = {
            {"Breakfast Buffet", String.format("Rs.%,.0f", bfRev), String.valueOf(bfRev), "#fbbf24"},
            {"WiFi Services", String.format("Rs.%,.0f", wifiRev), String.valueOf(wifiRev), "#60a5fa"},
            {"Parking", String.format("Rs.%,.0f", parkRev), String.valueOf(parkRev), "#34d399"},
            {"Laundry", String.format("Rs.%,.0f", lauRev), String.valueOf(lauRev), "#f87171"}
        };
        for (String[] a : addonInfo) {
            HBox lrow = new HBox();
            Label lbl = new Label(a[0]);
            lbl.setStyle(font(12) + fg(WHITE) + FF);
            Region rSp = new Region(); HBox.setHgrow(rSp, Priority.ALWAYS);
            Label amt = new Label(a[1]);
            amt.setStyle(fontB(12) + fg(WHITE) + FF);
            lrow.getChildren().addAll(lbl, rSp, amt);
            Rectangle track = new Rectangle(200, 7);
            track.setArcWidth(4); track.setArcHeight(4);
            track.setFill(Color.web(NAVY3));
            double fillW = (Double.parseDouble(a[2]) / maxAddon) * 200;
            Rectangle fill = new Rectangle(Math.max(2, fillW), 7);
            fill.setArcWidth(4); fill.setArcHeight(4);
            fill.setFill(Color.web(a[3]));
            StackPane bar = new StackPane(track, fill);
            bar.setAlignment(Pos.CENTER_LEFT);
            addonBarBox.getChildren().addAll(lrow, bar);
        }
    }

    // ===================== REFRESH UI =====================
    private void refreshAll() {
        roomTableData.clear();
        availRoomsData.clear();
        for (RoomModel r : rooms) {
            roomTableData.add(new RoomRow(r.number, r.type, r.basePrice, !r.occupied, r.floor));
            if (!r.occupied)
                availRoomsData.add(new RoomRow(r.number, r.type, r.basePrice, true, r.floor));
        }
        occupiedData.clear();
        for (Map.Entry<Integer, CustomerModel> e : activeGuests.entrySet()) {
            RoomModel rm = findRoom(e.getKey());
            String t = rm != null ? rm.type : "";
            occupiedData.add(new OccupiedRow(e.getKey(), e.getValue().name, t));
        }
        histData.clear();
        for (CustomerModel c : guestHistory)
            histData.add(new GuestHistoryRow(c.roomNo, c.name, c.contact, c.days, c.bill,
                                             c.wifi, c.breakfast, c.parking, c.status, c.idProof, c.roomType));
        updateDashboard();
        updateReports();
    }

    // ===================== DATABASE OPERATIONS =====================
    private void dbAddRoom(int roomNo, String type, double price, int floor) {
        String sql = "INSERT OR IGNORE INTO rooms(room_no,room_type,base_price,floor_no,occupied) VALUES(?,?,?,?,0)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roomNo); ps.setString(2, type);
            ps.setDouble(3, price); ps.setInt(4, floor);
            ps.executeUpdate();
        } catch (Exception e) { alert("DB Error (add room): " + e.getMessage()); }
    }

    private void dbDeleteRoom(int roomNo) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM rooms WHERE room_no=?")) {
            ps.setInt(1, roomNo); ps.executeUpdate();
        } catch (Exception e) { alert("DB Error (delete room): " + e.getMessage()); }
    }

    private void dbBookRoom(int rNo, String name, String contact, int days, double bill,
                             boolean wifi, boolean breakfast, boolean parking, boolean laundry,
                             String idProof, String roomType) {
        try {
            String ins = "INSERT OR REPLACE INTO active_guests" +
                         "(room_no,name,contact,id_proof,days,bill,wifi,breakfast,parking,laundry,paid,room_type)" +
                         "VALUES(?,?,?,?,?,?,?,?,?,?,0,?)";
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                ps.setInt(1, rNo); ps.setString(2, name); ps.setString(3, contact);
                ps.setString(4, idProof); ps.setInt(5, days); ps.setDouble(6, bill);
                ps.setInt(7, wifi?1:0); ps.setInt(8, breakfast?1:0);
                ps.setInt(9, parking?1:0); ps.setInt(10, laundry?1:0);
                ps.setString(11, roomType);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET occupied=1 WHERE room_no=?")) {
                ps.setInt(1, rNo); ps.executeUpdate();
            }
        } catch (Exception e) { alert("DB Error (book room): " + e.getMessage()); }
    }

    private void dbCheckout(int rNo, CustomerModel c) {
        try {
            String ins = "INSERT INTO guest_history" +
                         "(room_no,name,contact,id_proof,days,bill,wifi,breakfast,parking,laundry,status,room_type)" +
                         "VALUES(?,?,?,?,?,?,?,?,?,?,'CHECKED OUT',?)";
            try (PreparedStatement ps = conn.prepareStatement(ins)) {
                ps.setInt(1, rNo); ps.setString(2, c.name); ps.setString(3, c.contact);
                ps.setString(4, c.idProof != null ? c.idProof : "N/A");
                ps.setInt(5, c.days); ps.setDouble(6, c.bill);
                ps.setInt(7, c.wifi?1:0); ps.setInt(8, c.breakfast?1:0);
                ps.setInt(9, c.parking?1:0); ps.setInt(10, c.laundry?1:0);
                ps.setString(11, c.roomType);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM active_guests WHERE room_no=?")) {
                ps.setInt(1, rNo); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE rooms SET occupied=0 WHERE room_no=?")) {
                ps.setInt(1, rNo); ps.executeUpdate();
            }
        } catch (Exception e) { alert("DB Error (checkout): " + e.getMessage()); }
    }

    // ===================== LOAD FROM DB =====================
    private void loadAll() {
        rooms.clear(); activeGuests.clear(); guestHistory.clear();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM rooms ORDER BY room_no")) {
            while (rs.next()) {
                int fl = 1;
                try { fl = rs.getInt("floor_no"); } catch (Exception ignored) {}
                rooms.add(new RoomModel(rs.getInt("room_no"), rs.getString("room_type"),
                                         rs.getDouble("base_price"), rs.getInt("occupied") == 1, fl));
            }
        } catch (Exception e) { alert("DB Error (load rooms): " + e.getMessage()); }

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM active_guests")) {
            while (rs.next()) {
                String idp = "Aadhaar Card";
                try { idp = rs.getString("id_proof"); } catch (Exception ignored) {}
                boolean park = false, lau = false;
                try { park = rs.getInt("parking") == 1; lau = rs.getInt("laundry") == 1; } catch (Exception ignored) {}
                String rt = "STANDARD";
                try { rt = rs.getString("room_type"); } catch (Exception ignored) {}
                CustomerModel c = new CustomerModel(
                    rs.getString("name"), rs.getString("contact"),
                    rs.getInt("room_no"), rs.getInt("days"), rs.getDouble("bill"),
                    rs.getInt("wifi") == 1, rs.getInt("breakfast") == 1,
                    park, lau, rs.getInt("paid") == 1, idp, rt);
                activeGuests.put(c.roomNo, c);
            }
        } catch (Exception e) { alert("DB Error (load guests): " + e.getMessage()); }

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM guest_history ORDER BY id DESC")) {
            while (rs.next()) {
                String idp = "Aadhaar Card";
                try { idp = rs.getString("id_proof"); } catch (Exception ignored) {}
                boolean park = false, lau = false;
                try { park = rs.getInt("parking") == 1; lau = rs.getInt("laundry") == 1; } catch (Exception ignored) {}
                String rt = "STANDARD";
                try { rt = rs.getString("room_type"); } catch (Exception ignored) {}
                CustomerModel c = new CustomerModel(
                    rs.getString("name"), rs.getString("contact"),
                    rs.getInt("room_no"), rs.getInt("days"), rs.getDouble("bill"),
                    rs.getInt("wifi") == 1, rs.getInt("breakfast") == 1,
                    park, lau, true, idp, rt);
                c.status = rs.getString("status");
                guestHistory.add(c);
            }
        } catch (Exception e) { alert("DB Error (load history): " + e.getMessage()); }
    }

    // ===================== HELPER WIDGETS =====================
    private TextField styledField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setStyle(FIELD);
        tf.setPrefHeight(38);
        tf.setMaxWidth(Double.MAX_VALUE);
        return tf;
    }

    private Label styledLabel(String text, int size, String color) {
        Label l = new Label(text);
        l.setStyle(font(size) + fg(color) + FF + "-fx-letter-spacing:0.5;");
        return l;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle(font(10) + fg(SLATE) + FF + "-fx-letter-spacing:1;-fx-font-weight:bold;");
        return l;
    }

    private CheckBox styledCheck(String text) {
        CheckBox cb = new CheckBox(text);
        cb.setStyle(font(12) + fg(NAVY) + FF);
        return cb;
    }

    private VBox fieldWrap(String label, javafx.scene.Node field) {
        VBox wrap = new VBox(4);
        Label lbl = new Label(label.toUpperCase());
        lbl.setStyle(font(10) + fg(SLATE) + FF + "-fx-letter-spacing:0.6;");
        wrap.getChildren().addAll(lbl, field);
        return wrap;
    }

    @SuppressWarnings("unchecked")
    private <T, S> TableColumn<T, S> col(String name, String prop, double w) {
        TableColumn<T, S> c = new TableColumn<>(name);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(w);
        c.setStyle(FF);
        return c;
    }

    private Button colorBtn(String text, String bgColor, String fgColor) {
        Button b = new Button(text);
        b.setStyle(bg(bgColor) + fg(fgColor) + fontB(13) + rad(8) + brad(8) + FF +
                   pad(9, 18) + "-fx-cursor:hand;-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.2),4,0,0,2);");
        b.setOnMouseEntered(e -> { b.setOpacity(0.9); b.setScaleX(1.03); b.setScaleY(1.03); });
        b.setOnMouseExited(e  -> { b.setOpacity(1.0); b.setScaleX(1.0);  b.setScaleY(1.0);  });
        return b;
    }

    private Label badge(String text, String fgColor, String bgColor) {
        Label l = new Label(text);
        l.setStyle(font(11) + fg(fgColor) + bg(bgColor) + FF +
                   pad(4, 10) + rad(20) + brad(20));
        return l;
    }

    private Label placeholder(String msg) {
        Label l = new Label(msg);
        l.setStyle(font(12) + fg(SLATE2) + FF);
        return l;
    }

    private RoomModel findRoom(int n) {
        for (RoomModel r : rooms) if (r.number == n) return r;
        return null;
    }

    private void alert(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle("Grand Heritage");
        a.showAndWait();
    }

    public static void main(String[] args) { launch(args); }
}