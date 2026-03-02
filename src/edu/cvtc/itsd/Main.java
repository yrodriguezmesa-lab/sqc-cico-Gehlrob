package edu.cvtc.itsd;

// Import statements //////////////////////////////////////////////////////////
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.*;
import java.util.TimerTask;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;

// CiCo application's primary class ///////////////////////////////////////////
public class Main {
  // Constants ////////////////////////////////////////////////////////////////
  // Current application version.
  private static final int VERSION = 100;

  // Unique strings to identify card panels.
  private static final String CARD_MAIN = "Main";
  private static final String CARD_STATE = "State";
  private static final String CARD_ERROR = "Error";

  // Unique error codes.
  private static final int ERROR_UNKNOWN = 0;
  private static final int ERROR_NO_DB = 1;
  private static final int ERROR_NOT_FOUND = 2;
  private static final int ERROR_UPDATE_FAILED = 3;
  private static final int ERROR_INSERT_FAILED = 4;

  // Timeouts. Note the units.
  private static final long TIMEOUT_PANEL_MS = 10 * 1000;
  private static final int TIMEOUT_STATEMENT_S = 5;
  private static JButton doneButton;

  // Internal classes ///////////////////////////////////////////////////////////
  // InputFilter manages user input to the card number field.
  private static class InputFilter extends DocumentFilter {
    private static final int MAX_LENGTH = 8;

    @Override
    public void insertString(FilterBypass fb, int offset, String stringToAdd, AttributeSet attr)
        throws BadLocationException
    {
      if (fb.getDocument() != null) {
        super.insertString(fb, offset, stringToAdd, attr);
      }
      else {
        Toolkit.getDefaultToolkit().beep();
      }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int lengthToDelete, String stringToAdd, AttributeSet attr)
        throws BadLocationException
    {
      if (fb.getDocument() != null) {
        super.replace(fb, offset, lengthToDelete, stringToAdd, attr);
      }
      else {
        Toolkit.getDefaultToolkit().beep();
      }
    }
  }

  // Lookup the card information after button press ///////////////////////////
  public static class Update implements ActionListener {
    public void actionPerformed(ActionEvent evt) {
      Main.processCard();
    }
  }

  // Revert to the main panel after a button press ////////////////////////////
  public static class Handler implements ActionListener {
    public void actionPerformed(ActionEvent evt) {
      Main.doneProcessing();
    }
  }

  // Revert to the main panel after time has passed ///////////////////////////
  public static class Timeout extends TimerTask {
    public void run() {
      Main.doneProcessing();
    }
  }

  // Called when closing the application //////////////////////////////////////
  public static class OnShutdown implements Runnable {
    public void run() {
      try {
        statementQueryCard.close();
        statementUpdateMember.close();
        statementUpdateLog.close();
        db.close();
        System.out.println("Clean shutdown");
      }
      catch (SQLException e) {
        System.err.println(e.getMessage());
      }
    }
  }

  // GUI variables ////////////////////////////////////////////////////////////
  static JPanel deck;
  static JTextField fieldNumber;
  static JLabel labelReason;
  static JLabel labelUser;
  static JLabel labelState;
  static JButton buttonAcknowledge;

  // Timer variables //////////////////////////////////////////////////////////
  static java.util.Timer timer;
  static Timeout timeout;

  // Database and SQL variables ///////////////////////////////////////////////
  static Connection db;
  static PreparedStatement statementQueryCard;
  static PreparedStatement statementUpdateMember;
  static PreparedStatement statementUpdateLog;

  // Lookup a card number, toggle the status, and log the new status //////////
  private static void processCard() {
    if (db == null) {
      showError(ERROR_NO_DB);
      return;
    }

    ResultSet rows = null;
    try {
      statementQueryCard.setString(1, fieldNumber.getText());
      rows = statementQueryCard.executeQuery();
      if (rows.next()) {
        int id = rows.getInt("id");
        String name = rows.getString("name");
        int currentState = rows.getInt("is_checked_in");
        currentState = (currentState + 1) % 2;

        statementUpdateMember.setInt(1, currentState);
        statementUpdateMember.setInt(2, id);
        int numChanged = statementUpdateMember.executeUpdate();
        if (numChanged != 1) {
          showError(ERROR_UPDATE_FAILED);
          return;
        }

        statementUpdateLog.setInt(1, id);
        statementUpdateLog.setInt(2, currentState);
        int numInserted = statementUpdateLog.executeUpdate();
        if (numInserted != 1) {
          showError(ERROR_INSERT_FAILED);
        }

        updateStateLabels(name, currentState == 1);
        scheduleTransitionFrom(CARD_STATE, doneButton);
      }
      else {
        showError(ERROR_NOT_FOUND);
      }
    }
    catch (SQLException e) {
      System.err.println(e.getMessage());
      showError(ERROR_UNKNOWN);
    }
    finally {
      try {
        if (rows != null) rows.close();
      }
      catch (SQLException e2) {
        System.err.println(e2.getMessage());
        showError(ERROR_UNKNOWN);
      }
    }
  }

  // Display errors to users //////////////////////////////////////////////////
  private static void showError(int code) {
    // Module 2 ticket: Show human-readable error messages.
    String[] explanations = {
        "Please inform staff an unknown error occurred.",
        "Please inform staff that database wasn't found.",
        "Please show your card to staff to validate.",
        "Please inform staff that status updates failed.",
        "Please inform staff that log updates failed."
    };

    labelReason.setText(explanations[code]);
    scheduleTransitionFrom(CARD_ERROR, buttonAcknowledge);
  }

  // Create an idle timer and display the target card /////////////////////////
  private static void scheduleTransitionFrom(String fromCard, JButton toFocus) {
    if (timeout != null) {
      timeout.cancel();
    }
    timeout = new Timeout();
    timer.schedule(timeout, TIMEOUT_PANEL_MS);
    ((CardLayout)deck.getLayout()).show(deck, fromCard);
    if (toFocus != null) {
      toFocus.grabFocus();
    }
  }

  // Return to the main panel /////////////////////////////////////////////////
  private static void doneProcessing() {
    timeout.cancel();
    timeout = null;
    fieldNumber.setText("");
    ((CardLayout)deck.getLayout()).show(deck, CARD_MAIN);
    fieldNumber.grabFocus();
  }

  // Display name and new status //////////////////////////////////////////////
  // Module 3 tickets: Display user name and new status. Doesn't require a
  // method and can be done where this is called instead.
  private static void updateStateLabels(String name, boolean isCheckedInNow) {
    labelUser.setText(name);
    labelState.setText(isCheckedInNow ? "Checked IN" : "Checked OUT");
  }

  // Entry point //////////////////////////////////////////////////////////////
  // Our GUI code is very similar; however, we want to keep it explicit.
  @SuppressWarnings("DuplicatedCode")
  public static void main(String[] args) {
    // Initialize variables.
    db = null;
    timer = new java.util.Timer("CiCo timeout timer");
    Handler handler = new Handler();

    // Create our GUI.
    JFrame frame = new JFrame();
    frame.setMinimumSize(new Dimension(320, 240));
    frame.setPreferredSize(new Dimension(640, 480));
    frame.setMaximumSize(new Dimension(640, 480));

    // Collect each "card" panel in a deck.
    deck = new JPanel(new CardLayout());
    Font fontMain = new Font(Font.SANS_SERIF, Font.PLAIN, 24);

    // Main panel /////////////////////////////////////////////////////////////
    JPanel panelMain = new JPanel();
    panelMain.setLayout(new BoxLayout(panelMain, BoxLayout.PAGE_AXIS));
    panelMain.setMinimumSize(new Dimension(320, 240));
    panelMain.setPreferredSize(new Dimension(640, 480));
    panelMain.setMaximumSize(new Dimension(640, 480));
    panelMain.setBackground(Color.black);

    panelMain.add(Box.createVerticalGlue());
    JLabel labelDirective = new JLabel("Scan card", JLabel.LEADING);
    labelDirective.setFont(fontMain);
    labelDirective.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelDirective.setForeground(Color.cyan);
    panelMain.add(labelDirective);

    fieldNumber = new JTextField();
    InputFilter filter = new InputFilter();
    ((AbstractDocument)(fieldNumber.getDocument())).setDocumentFilter(filter);
    fieldNumber.setPreferredSize(new Dimension(200, 32));
    fieldNumber.setMaximumSize(new Dimension(200, 32));
    fieldNumber.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    fieldNumber.setBackground(Color.green);
    fieldNumber.setForeground(Color.magenta);
    panelMain.add(fieldNumber);

    JButton updateButton = new JButton("Update");
    updateButton.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    updateButton.addActionListener(new Update());
    updateButton.setForeground(Color.green);
    panelMain.add(updateButton);

    panelMain.add(Box.createVerticalGlue());

    // Status panel ///////////////////////////////////////////////////////////
    JPanel panelStatus = new JPanel();
    panelStatus.setLayout(new BoxLayout(panelStatus, BoxLayout.PAGE_AXIS));
    panelStatus.setMinimumSize(new Dimension(320, 240));
    panelStatus.setPreferredSize(new Dimension(640, 480));
    panelStatus.setMaximumSize(new Dimension(640, 480));
    panelStatus.setBackground(Color.blue);
    
    JButton doneButton = new JButton("Done");
    doneButton.addActionListener(handler);
    doneButton.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    panelStatus.add(doneButton);

    panelStatus.add(Box.createVerticalGlue());
    labelUser = new JLabel("Registrant", JLabel.LEADING);
    labelUser.setFont(fontMain);
    labelUser.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelUser.setForeground(Color.yellow);
    panelStatus.add(labelUser);

    labelState = new JLabel("updated", JLabel.LEADING);
    labelState.setFont(fontMain);
    labelState.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelState.setForeground(Color.magenta);
    panelStatus.add(labelState);

    panelStatus.add(Box.createVerticalGlue());

    // Error panel ////////////////////////////////////////////////////////////
    JPanel panelError = new JPanel();
    panelError.setLayout(new BoxLayout(panelError, BoxLayout.PAGE_AXIS));
    panelError.setMinimumSize(new Dimension(320, 240));
    panelError.setPreferredSize(new Dimension(640, 480));
    panelError.setMaximumSize(new Dimension(640, 480));
    panelError.setBackground(Color.red);

    panelError.add(Box.createVerticalGlue());
    labelReason = new JLabel("", JLabel.LEADING);
    labelReason.setFont(fontMain);
    labelReason.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    labelReason.setForeground(Color.yellow);
    panelError.add(labelReason);

    buttonAcknowledge = new JButton("OK");
    buttonAcknowledge.addActionListener(handler);
    buttonAcknowledge.setAlignmentX(JComponent.CENTER_ALIGNMENT);
    buttonAcknowledge.setForeground(Color.red);
    panelError.add(buttonAcknowledge);
    panelError.add(Box.createVerticalGlue());

    // Add the cards //////////////////////////////////////////////////////////
    deck.add(panelMain, CARD_MAIN);
    deck.add(panelStatus, CARD_STATE);
    deck.add(panelError, CARD_ERROR);
    frame.getContentPane().add(deck, BorderLayout.CENTER);

    // Module 2 ticket: Add version number.
    JLabel labelMeta = new JLabel("CiCo v" + VERSION);
    labelMeta.setOpaque(true);
    labelMeta.setBackground(Color.darkGray);
    labelMeta.setForeground(Color.white);
    labelMeta.setBorder(new EmptyBorder(10, 10, 10, 10));
    labelMeta.setMinimumSize(new Dimension(320, 32));
    labelMeta.setPreferredSize(new Dimension(640, 32));
    labelMeta.setMaximumSize(new Dimension(640, 32));
    frame.getContentPane().add(labelMeta, BorderLayout.PAGE_END);

    // Connect to DB //////////////////////////////////////////////////////////
    try {
      //noinspection SpellCheckingInspection
      db = DriverManager.getConnection("jdbc:sqlite:cico.db");
      Statement command = db.createStatement();
      command.setQueryTimeout(TIMEOUT_STATEMENT_S);

      // Create the tables if needed.
      command.executeUpdate("CREATE TABLE IF NOT EXISTS members (id INTEGER PRIMARY KEY, name TEXT NOT NULL, card TEXT NOT NULL, is_checked_in INTEGER)");
      command.executeUpdate("CREATE TABLE IF NOT EXISTS log (members_id INTEGER, is_checked_in INTEGER, at TEXT NOT NULL)");

      // 99999999 is guaranteed invalid.
      command.executeUpdate("DELETE FROM members WHERE card = '99999999'");

      // 00000000 is guaranteed valid; create if needed.
      command.executeUpdate("INSERT INTO members (name, card, is_checked_in) SELECT 'Developer', '00000000', 0 WHERE NOT EXISTS (SELECT name, card, is_checked_in FROM members WHERE card = '00000000')");

      // Create parameterized SQL statements.
      statementQueryCard = db.prepareStatement("SELECT id, name, is_checked_in FROM members WHERE card = ? LIMIT 1");
      statementUpdateMember = db.prepareStatement("UPDATE members SET is_checked_in = ? WHERE id = ?");
      statementUpdateLog = db.prepareStatement("INSERT INTO log (members_id, is_checked_in, at) VALUES (?, ?, datetime())");

      // Close the database and prepared statements on exit.
      Runtime.getRuntime().addShutdownHook(new Thread(new OnShutdown()));
    }
    catch (SQLException e) {
      System.err.println(e.getMessage());
      db = null;
    }

    // Display the GUI ////////////////////////////////////////////////////////
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }
}
