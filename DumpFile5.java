/*
  Dump File #5 - Dump Files in Hexadecimal and as Text
  Written by: Keith Fenske, http://kwfenske.github.io/
  Thursday, 8 February 2007
  Java class name: DumpFile5
  Copyright (c) 2007 by Keith Fenske.  Apache License or GNU GPL.

  This is a Java 1.4 application to dump the contents of files in hexadecimal
  and as 8-bit text bytes.  For example, using eight input bytes per line of
  the dump output:

      Dumping file: C:\MSDOS.SYS
      00000000  3B 53 59 53 0D 0A 5B 50  |;SYS..[P|
      00000008  61 74 68 73 5D 0D 0A 57  |aths]..W|
      00000010  69 6E 44 69 72 3D 43 3A  |inDir=C:|
         ...
      000006E8  73 0D 0A 0D 0A           |s....   |
      1,773 bytes dumped.

  Choose your options; then click on the "Open Files" button and select one or
  more files to be dumped.  Output will be shown in a scrolling text area, and
  this output can be saved into a text file with the "Save Output As" button or
  by copying and pasting from the text area.

  Apache License or GNU General Public License
  --------------------------------------------
  DumpFile5 is free software and has been released under the terms and
  conditions of the Apache License (version 2.0 or later) and/or the GNU
  General Public License (GPL, version 2 or later).  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the license(s) for more details.  You should have
  received a copy of the licenses along with this program.  If not, see the
  http://www.apache.org/licenses/ and http://www.gnu.org/licenses/ web pages.

  Graphical Versus Console Application
  ------------------------------------
  The Java command line may contain options or file names.  If no file names
  are given on the command line, then this program runs as a graphical or "GUI"
  application with the usual dialog boxes and windows.  If one or more file
  names are given on the command line, then this program runs as a console
  application without a graphical interface.  See the "-?" option for a help
  summary:

      java  DumpFile5  -?

  The dump is written on standard output, and may be redirected with the ">" or
  "1>" operators.  (Standard error may be redirected with the "2>" operator.)
  An example command line is:

      java  DumpFile5  -w16  c:\msdos.sys  >d:\temp\filedump.txt

  The graphical interface can be very slow when the output text area gets too
  big.  The Java Swing components JScrollPane and JTextArea get themselves in
  knots trying to display more than a megabyte of scrolling output text.  The
  console application is much faster and uses a constant amount of virtual
  memory (under 6 MB), instead of a growing amount that consumes the entire
  Java heap and eventually generates "out of memory" errors.  The fastest way
  to use this program is from the command line with standard output redirected
  to a file.  Then open the output file in your favorite word processor or
  plain text editor.

  Restrictions and Limitations
  ----------------------------
  Input bytes are shown only as 7-bit or 8-bit ASCII text.  A period (".") is
  substituted for unprintable characters.  It would be possible to display
  pairs of bytes as simple 16-bit Unicode text.  However, none of the double-
  byte shifting character sets common in Chinese, Japanese, or Korean are
  practical because multi-byte character sequences will most likely be broken
  across lines in the dump output.  Extended Unicode characters can not be
  supported for the same reason, where multiple 16-bit values are combined into
  additional character codes.
*/

import java.awt.*;                // older Java GUI support
import java.awt.event.*;          // older Java GUI event support
import java.io.*;                 // standard I/O
import java.text.*;               // number formatting
import javax.swing.*;             // newer Java GUI support

public class DumpFile5
{
  /* constants */

  static final int BUFFER_SIZE = 0x10000; // input buffer size in bytes (64 KB)
  static final String COPYRIGHT_NOTICE =
    "Copyright (c) 2007 by Keith Fenske.  Apache License or GNU GPL.";
  static final String[] DUMP_WIDTHS = {"4", "8", "12", "16", "24", "32"};
                                  // number of input bytes per dump line
  static final String[] FONT_SIZES = {"10", "12", "14", "16", "18", "20", "24",
    "30"};                        // point sizes for text in output text area
  static final int OFFSET_DIGITS = 8; // hex digits in file offset (location)
  static final String PROGRAM_TITLE =
    "Dump Files in Hexadecimal and as Text - by: Keith Fenske";

  /* class variables */

  static JButton cancelButton;    // graphical button for <cancelFlag>
  static boolean cancelFlag;      // our signal from user to stop processing
  static boolean consoleFlag;     // true if running as a console application
  static int dumpStart;           // index where first hexadecimal digits go
  static int dumpWidth;           // number of input bytes per dump line
  static JComboBox dumpWidthDialog; // graphical option for <dumpWidth>
  static JCheckBox eightBitCheckbox; // graphical option for <eightBitFlag>
  static boolean eightBitFlag;     // true if we display bytes as 8-bit text
  static JButton exitButton;      // "Exit" button
  static JFileChooser fileChooser; // asks for input and output file names
  static String fontName;         // font name for text in output text area
  static JComboBox fontNameDialog; // graphical option for <fontName>
  static int fontSize;            // point size for text in output text area
  static JComboBox fontSizeDialog; // graphical option for <fontSize>
  static NumberFormat formatComma; // formats with commas (digit grouping)
  static JFrame mainFrame;        // this application's window if GUI
  static JButton openButton;      // "Open Files" button
  static File[] openFileList;     // list of files selected by user
  static Thread openFilesThread;  // separate thread for openFiles() method
  static int outputSize;          // characters needed in each dump buffer
  static JTextArea outputText;    // generated report
  static JButton saveButton;      // "Save Output As" button
  static int textStart;           // where first ASCII text character goes

/*
  main() method

  If we are running as a GUI application, set the window layout and then let
  the graphical interface run the show.
*/
  public static void main(String[] args)
  {
    ActionListener action;        // our shared action listener
    int i;                        // index variable
    String word;                  // one parameter from command line

    /* Initialize variables used by both console and GUI applications. */

    cancelFlag = false;           // don't cancel unless user complains
    consoleFlag = false;          // assume no file names on command line
    dumpWidth = 16;               // default input bytes per dump line
    eightBitFlag = false;         // default to display bytes as 8-bit text
    fontName = "Monospaced";      // default font name for output text area
    fontSize = 14;                // default point size for output text area
    outputText = null;            // write to standard output until GUI ready

    /* Initialize number formatting styles. */

    formatComma = NumberFormat.getInstance(); // current locale
    formatComma.setGroupingUsed(true); // use commas or digit groups

    /* Check command-line parameters for options.  Anything we don't recognize
    as an option is assumed to be a file name. */

    for (i = 0; i < args.length; i ++)
    {
      word = args[i].toLowerCase(); // easier to process if consistent case
      if (word.length() == 0)
      {
        /* Ignore empty parameters, which are more common than you might think,
        when programs are being run from inside scripts (command files). */
      }

      else if (word.equals("?") || word.equals("-?") || word.equals("/?")
        || word.equals("-h") || word.equals("/h")
        || word.equals("-help") || word.equals("/help"))
      {
        showHelp();               // show help summary
        System.exit(0);           // exit from application after printing help
      }

      else if (word.equals("-e") || word.equals("/e")
        || word.equals("-e1") || word.equals("/e1"))
      {
        eightBitFlag = true;      // display input bytes as 8-bit ASCII text
      }
      else if (word.equals("-e0") || word.equals("/e0"))
        eightBitFlag = false;     // display input bytes as 7-bit plain text

      else if (word.equals("-w4") || word.equals("/w4"))
        dumpWidth = 4;            // user wants 4 input bytes per dump line
      else if (word.equals("-w8") || word.equals("/w8"))
        dumpWidth = 8;
      else if (word.equals("-w12") || word.equals("/w12"))
        dumpWidth = 12;
      else if (word.equals("-w16") || word.equals("/w16"))
        dumpWidth = 16;
      else if (word.equals("-w24") || word.equals("/w24"))
        dumpWidth = 24;
      else if (word.equals("-w32") || word.equals("/w32"))
        dumpWidth = 32;

      else if ((word.charAt(0) == '-')
        || ((word.charAt(0) == '/') && (word.length() < 4)))
                                  // remember UNIX uses '/' for root folder!
      {
        System.err.println("Option not recognized: " + args[i]);
        showHelp();               // show help summary
        System.exit(0);           // exit from application after printing help
      }
      else
      {
        /* Parameter does not look like an option.  Assume this is a file name.
        We ignore <cancelFlag> because the user has no way of interrupting us
        at this point (no graphical interface). */

        consoleFlag = true;       // don't allow GUI methods to be called
        dumpFile(new File(args[i])); // original parameter, not lowercase word
      }
    }

    /* Start the graphical interface if no file names were given on the command
    line. */

    if (!consoleFlag)
    {
      /* The standard Java interface style is the most reliable, but you can
      switch to something closer to the local system, if you want. */

      try
      {
        UIManager.setLookAndFeel(
          UIManager.getCrossPlatformLookAndFeelClassName());
//        UIManager.getSystemLookAndFeelClassName());
      }
      catch (Exception ulafe)
      {
        System.err.println("Unsupported Java look-and-feel: " + ulafe);
      }

      /* Initialize shared graphical objects. */

      action = new DumpFile5User(); // create our shared action listener
      fileChooser = new JFileChooser(); // create our shared file chooser

      /* Create the graphical interface as a series of little panels inside
      bigger panels.  The intermediate panel names are of no lasting importance
      and hence are only numbered (panel1, panel2, etc). */

      /* Create a vertical box to stack buttons and options. */

      Box panel1 = new Box(BoxLayout.Y_AXIS);
      panel1.add(Box.createVerticalStrut(9)); // extra space at panel top

      /* Create a horizontal panel to hold the action buttons. */

      JPanel panel2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 5));

      openButton = new JButton("Open Files...");
      openButton.addActionListener(action);
      openButton.setMnemonic(KeyEvent.VK_O);
      openButton.setToolTipText("Select one or more files.");
      panel2.add(openButton);

      cancelButton = new JButton("Cancel");
      cancelButton.addActionListener(action);
      cancelButton.setEnabled(false);
      cancelButton.setMnemonic(KeyEvent.VK_C);
      cancelButton.setToolTipText("Stop finding/opening files.");
      panel2.add(cancelButton);

      saveButton = new JButton("Save Output As...");
      saveButton.addActionListener(action);
      saveButton.setMnemonic(KeyEvent.VK_S);
      saveButton.setToolTipText("Save output text in a file.");
      panel2.add(saveButton);

      exitButton = new JButton("Exit");
      exitButton.addActionListener(action);
      exitButton.setMnemonic(KeyEvent.VK_X);
      exitButton.setToolTipText("Close this program.");
      panel2.add(exitButton);

      panel1.add(panel2);
      panel1.add(Box.createVerticalStrut(2)); // extra space between panels

      /* Create a horizontal panel for options. */

      JPanel panel3 = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));

      fontNameDialog = new JComboBox(GraphicsEnvironment
        .getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
      fontNameDialog.setEditable(false); // user must select one of our choices
      fontNameDialog.setSelectedItem(fontName); // select default font name
      fontNameDialog.setToolTipText("Font name for displayed text.");
      fontNameDialog.addActionListener(action); // do last so don't fire early
      panel3.add(fontNameDialog);

      fontSizeDialog = new JComboBox(FONT_SIZES);
      fontSizeDialog.setEditable(false); // user must select one of our choices
      fontSizeDialog.setSelectedItem(String.valueOf(fontSize));
                                  // selected item is our default size
      fontSizeDialog.setToolTipText("Point size for displayed text.");
      fontSizeDialog.addActionListener(action); // do last so don't fire early
      panel3.add(fontSizeDialog);

      panel3.add(Box.createHorizontalStrut(40));

      dumpWidthDialog = new JComboBox(DUMP_WIDTHS);
      dumpWidthDialog.setEditable(false); // user must select one of our choices
      dumpWidthDialog.setSelectedItem(String.valueOf(dumpWidth));
                                  // selected item is our default size
      dumpWidthDialog.setToolTipText("Number of input bytes per dump line.");
//    dumpWidthDialog.addActionListener(action); // do last so don't fire early
      panel3.add(dumpWidthDialog);
      panel3.add(new JLabel("bytes per line"));

      panel3.add(Box.createHorizontalStrut(30));

      eightBitCheckbox = new JCheckBox("8-bit text", eightBitFlag);
      eightBitCheckbox.setToolTipText(
        "Select to display input bytes as 8-bit ASCII text.");
      panel3.add(eightBitCheckbox);

      panel1.add(panel3);
      panel1.add(Box.createVerticalStrut(1)); // extra space at panel bottom

      /* Put above boxed options in a panel that is centered horizontally. */

      JPanel panel4 = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
      panel4.add(panel1);

      /* Create a scrolling text area to hold the generated output. */

      outputText = new JTextArea(6, 30);
      outputText.setEditable(false); // user can't change this text area
      outputText.setFont(new Font(fontName, Font.PLAIN, fontSize));
      outputText.setLineWrap(false); // don't allow text lines to wrap
      outputText.setMargin(new Insets(10, 12, 10, 12));
      outputText.setText(
        "\nDump files in hexadecimal and as 8-bit text bytes."
        + "\n\nChoose your options; then open files that you want to dump.\n\n"
        + COPYRIGHT_NOTICE + "\n\n");

      /* Create the main window frame for this application.  Stack buttons and
      options above the text area.  Keep text in the center so that it expands
      horizontally and vertically. */

      mainFrame = new JFrame(PROGRAM_TITLE);
      Container panel5 = mainFrame.getContentPane(); // where content meets frame
      panel5.setLayout(new BorderLayout(5, 5));
      panel5.add(panel4, BorderLayout.NORTH);
      panel5.add(new JScrollPane(outputText), BorderLayout.CENTER);

      mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      mainFrame.setLocation(50, 50); // top left corner of application window
      mainFrame.setSize(700, 500); // initial size of application window
      mainFrame.validate();       // do the application window layout
      mainFrame.setVisible(true); // show the application window

      /* Let the graphical interface run the application now. */
    }
  } // end of main() method

// ------------------------------------------------------------------------- //

/*
  cancelOpen() method

  This method is called while we are opening files if the user wants to end
  processing early, perhaps because it is taking too long.  We must cleanly
  terminate any secondary threads.  Leave whatever output has already been
  generated in the output text area.
*/
  static void cancelOpen()
  {
    cancelFlag = true;            // tell other threads that all work stops now
    putOutput("Cancelled by user.");

  } // end of cancelOpen() method


/*
  clearLine() method

  Clear a line buffer to all spaces (blanks).  The buffer is assumed to be
  <outputSize> bytes long.
*/
  static void clearLine(StringBuffer buffer)
  {
    int i;                        // index variable

    for (i = 0; i < outputSize; i ++)
      buffer.setCharAt(i, ' ');   // replace one byte/character at a time

  } // end of clearLine() method


/*
  compareLine() method

  Compare two line buffers to see if they are the same, except for the file
  offset at the beginning of each line.  Return <true> if they are the same;
  <false> othersize.  The buffers are assumed to be <outputSize> bytes long.
*/
  static boolean compareLine(StringBuffer first, StringBuffer second)
  {
    int i;                        // index variable

    for (i = dumpStart; i < outputSize; i ++)
    {
      if (first.charAt(i) != second.charAt(i))
        return (false);           // only need to find one difference
    }
    return (true);                // all characters are the same

  } // end of compareLine() method


/*
  dumpFile() method

  Dump the contents of one file in hexadecimal and as 8-bit ASCII bytes.  The
  caller gives us a File object to tell us which file, and this File object may
  or may not be valid.
*/
  static void dumpFile(File givenFile)
  {
    int c;                        // input character
    int dumpOffset;               // where next hexadecimal bytes go in buffer
    long fileOffset;              // byte offset from start of input file
    FileInputStream in;           // input file stream
    byte[] inputBuffer;           // three times faster than read() each byte
    int inputIndex;               // index of next input byte in <inputBuffer>
    int inputRead;                // number of bytes actually read into buffer
    int lineUsed;                 // number of input bytes dumped in this line
    StringBuffer newBuffer;       // current output line in dump
    StringBuffer oldBuffer;       // previous output line in dump
    int sameCount;                // number of identical dump lines found
    StringBuffer tempBuffer;      // temporary holder for switching buffers
    int textOffset;               // where next ASCII text goes in buffer

    putOutput("");                // blank line
    if (givenFile.isFile())       // only if a file, not a folder or unknown
    {
      /* Calculate some variables that are critical constants for inserting
      text into each "line buffer" for the dump output. */

      dumpStart = OFFSET_DIGITS + 2; // index for first byte as hex digits
      textStart = dumpStart + (3 * dumpWidth) + 2;
                                  // index for first byte as ASCII text
      outputSize = textStart + dumpWidth + 1;
                                  // exact buffer size needed for one line

      /* Try to open the user's file and start dumping. */

      putOutput("Dumping file: " + givenFile.getPath());
      try
      {
        in = new FileInputStream(givenFile); // open file for reading bytes

        dumpOffset = dumpStart;   // where first digits go in this dump line
        fileOffset = 0;           // we are at the beginning of the file
        inputBuffer = new byte[BUFFER_SIZE]; // allocate byte buffer for input
        lineUsed = 0;             // nothing dumped in this line yet
        sameCount = 0;            // no identical dump lines found yet
        textOffset = textStart;   // where first text chars go in dump line

        newBuffer = new StringBuffer(outputSize); // allocate dump buffer
        newBuffer.setLength(outputSize); // and force buffer to have that size
        startBuffer(newBuffer, fileOffset); // clear buffer, insert file offset

        oldBuffer = new StringBuffer(outputSize); // allocate dump buffer
        oldBuffer.setLength(outputSize); // and force buffer to have that size
        clearLine(oldBuffer);     // clear to spaces

        while ((inputRead = in.read(inputBuffer)) > 0)
        {
          if (cancelFlag) break;  // stop if user hit the panic button

          for (inputIndex = 0; inputIndex < inputRead; inputIndex ++)
          {
            if (cancelFlag) break; // stop if user hit the panic button

            c = ((int) inputBuffer[inputIndex]) & 0x000000FF; // unsigned byte

            if (lineUsed >= dumpWidth)
            {
              /* We have filled up the current dump buffer.  If it differs from
              the previous dump buffer, then print it.  Otherwise, leave it
              pending. */

              if (compareLine(oldBuffer, newBuffer) == false)
              {
                /* This dump line differs from the previous line.  There may be
                a single line that hasn't been printed yet. */

                if (sameCount == 1) // was exactly one duplicate line found?
                  printLine(oldBuffer); // yes, print single line, not ellipses
                sameCount = 0;    // now ignore previous dump lines

                /* Print the new dump line. */

                printLine(newBuffer); // print new dump line

                /* Switch the old and new buffer pointers, to avoid copying the
                new dump buffer's contents into the old dump buffer. */

                tempBuffer = oldBuffer; // save pointer to old buffer object
                oldBuffer = newBuffer; // switch new and old object pointers
                newBuffer = tempBuffer;
              }
              else
              {
                /* This dump line has the same contents as the previous dump
                line (except for the file offset, of course). */

                sameCount ++;     // increment number of identical dump lines

                if (sameCount == 1)
                {
                  /* This is the first time we have seen the same dump line.
                  We may need to remember this line, if we later find that it
                  occurs only once (and gets printed). */

                  tempBuffer = oldBuffer; // save pointer to old buffer object
                  oldBuffer = newBuffer; // switch new and old object pointers
                  newBuffer = tempBuffer;
                }
                else if (sameCount == 2)
                {
                  /* This is the second time we have seen the same dump line.
                  Print ellipses (dots) to represent these two and any later
                  occurrences. */

                  putOutput("   ...", false); // print one line of ellipses
                }
                else
                {
                  /* Do nothing for third or following occurrence. */
                }
              }

              dumpOffset = dumpStart; // next hex digits start at beginning
              lineUsed = 0;       // no dumped bytes in buffer now
              textOffset = textStart; // next text chars start at beginning

              startBuffer(newBuffer, fileOffset); // put file offset in buffer
            }

            putHex(newBuffer, dumpOffset, 2, c); // convert char to hex digits
            dumpOffset += 3;      // where next hexadecimal digits go

            if ((c < 0x20)        // check for unprintable characters
              || ((eightBitFlag == false) && (c >= 0x7F))
              || ((eightBitFlag == true) && (c == 0x7F)))
            {
              newBuffer.setCharAt(textOffset, '.'); // substitute
            }
            else
              newBuffer.setCharAt(textOffset, (char) c); // use original char
            textOffset ++;        // where next text character goes

            fileOffset ++;        // offset of next input byte in file
            lineUsed ++;          // one more dumped byte in line buffer

          } // end of for loop per input byte

        } // end of while read buffer loop

        in.close();               // close input file

        /* If the user hasn't cancelled this operation, finish printing the
        dump if any output lines are pending, and then print a summary. */

        if (!cancelFlag)          // don't do more work if cancelled by user
        {
          /* Always print last line in the dump, even if the file is empty. */

          if (sameCount == 1)     // any pending single duplicate line?
            printLine(oldBuffer); // yes, print the duplicate line first
          printLine(newBuffer);   // then print the partial last line

          /* Append a summary. */

          putOutput(formatComma.format(fileOffset) + " bytes dumped.");
          putOutput("");          // blank line
        }
      }
      catch (IOException ioe)
      {
        putOutput("Can't read from input file: " + ioe.getMessage());
      }
    }
    else
    {
      putOutput("Sorry, " + givenFile.getPath() + " is not a file.");
    }
  } // end of dumpFile() method


/*
  openFiles() method

  Ask the user for a list of file names.  We accept all files without any
  filtering for file types.
*/
  public static void openFiles()
  {
    /* Get options as chosen by the user. */

    dumpWidth = Integer.parseInt((String) dumpWidthDialog.getSelectedItem());
                                  // safe to parse since we supply the choices
    eightBitFlag = eightBitCheckbox.isSelected();

    /* Ask the user for one or more file names. */

    fileChooser.resetChoosableFileFilters(); // remove any existing filters
    fileChooser.setDialogTitle("Open Files...");
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(true); // allow more than one file
    if (fileChooser.showOpenDialog(mainFrame) != JFileChooser.APPROVE_OPTION)
      return;                     // user cancelled file selection dialog box
    openFileList = fileChooser.getSelectedFiles();
                                  // get list of files selected by user

    /* We have a list of files.  Disable the "Open Files" button until we are
    done, and enable a "Cancel" button in case our secondary thread runs for a
    long time and the user panics. */

    cancelButton.setEnabled(true); // enable button to cancel this processing
    cancelFlag = false;           // but don't cancel unless user complains
    openButton.setEnabled(false); // suspend "Open Files" until we are done
    outputText.setText("");       // clear output text area

    openFilesThread = new Thread(new DumpFile5User(), "openFilesRunner");
    openFilesThread.setPriority(Thread.MIN_PRIORITY);
                                  // use low priority for heavy-duty workers
    openFilesThread.start();      // run separate thread to open files, report

  } // end of openFiles() method


/*
  openFilesRunner() method

  This method is called inside a separate thread by the runnable interface of
  our "user" class to process the user's selected files in the context of the
  "main" class.  By doing all the heavy-duty work in a separate thread, we
  won't stall the thread that runs the graphical interface, and we allow the
  user to cancel the work if it takes too long.
*/
  static void openFilesRunner()
  {
    int i;                        // index variable

    /* Loop once for each file selected by the user.  Don't assume that these
    names are all valid. */

    for (i = 0; i < openFileList.length; i ++)
    {
      if (cancelFlag) break;      // stop if user hit the panic button
      dumpFile(openFileList[i]);  // dump contents of one input file
    }

    /* We are done, so turn off the "Cancel" button and allow the user to click
    the "Open Files" button again. */

    cancelButton.setEnabled(false);
    openButton.setEnabled(true);

  } // end of openFilesRunner() method


/*
  printLine() method

  Print a given StringBuffer as one output line, after trimming any trailing
  spaces.  No assumptions are made about the length of the buffer.

  We actually never have trailing spaces on dump lines because we add marker
  characters to the beginning and end of the ASCII text.  However, we still
  need to convert the StringBuffer to a String object before we can print it,
  so this trimming effort isn't totally wasted.
*/
  static void printLine(StringBuffer buffer)
  {
    int i;                        // index variable

    i = buffer.length() - 1;      // start trimming from the end
    while ((i >= 0) && (buffer.charAt(i) == ' '))
      i --;
    putOutput(buffer.substring(0, (i + 1)), false); // don't scroll dump lines

  } // end of printLine() method


/*
  putHex() method

  Convert a signed long integer to an unsigned hexadecimal string with the
  number of digits specified by the caller.  Put the result in the caller's
  line buffer at a specified location.
*/
  static void putHex(
    StringBuffer buffer,          // line buffer where our output goes
    int offset,                   // starting offset in buffer
    int digits,                   // how many hexadecimal digits to convert
    long number)                  // convert this binary number to hexadecimal
  {
    /* constants */

    final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'}; // hexadecimal digits
    final long hexMask = 0x0F;    // bitmask for last hexadecimal digit
    final int hexShift = 4;       // bits to shift each hexadecimal digit

    /* local variables */

    int i;                        // index variable
    long rem;                     // remaining number to do

    /* procedure */

    rem = number;                 // start with the whole number
    for (i = (offset + digits - 1); i >= offset; i --)
    {
      buffer.setCharAt(i, hexDigits[(int) (rem & hexMask)]);
                                  // put one hexadecimal digit
      rem = rem >> hexShift;      // next piece to do
    }
  } // end of putHex() method


/*
  putOutput() method

  Append a complete line of text to the end of the output text area.  We add a
  newline character at the end of the line, not the caller.  By forcing all
  output to go through this same method, one complete line at a time, the
  generated output is cleaner and can be redirected.

  The output text area is forced to scroll to the end, after the text line is
  written, by selecting character positions that are much too large (and which
  are allowed by the definition of the JTextComponent.select() method).  This
  is easier and faster than manipulating the scroll bars directly.
*/
  static void putOutput(String text)
  {
    putOutput(text, true);        // default action is to scroll output lines
  }

  static void putOutput(String text, boolean scroll)
  {
    if (outputText == null)
      System.out.println(text);   // console output goes onto standard output
    else
    {
      outputText.append(text + "\n"); // graphical output goes into text area
      if (scroll)                 // does caller want us to scroll?
        outputText.select(999999999, 999999999); // force scroll to end of text
    }
  }


/*
  saveOutputText() method

  Ask the user for an output file name, create or replace that file, and copy
  the contents of our output text area to that file.  The output file will be
  in the default character set for the system, so if there are special Unicode
  characters in the displayed text (Arabic, Chinese, Eastern European, etc),
  then you are better off copying and pasting the output text directly into a
  Unicode-aware application like Microsoft Word.
*/
  static void saveOutputText()
  {
    FileWriter output;            // output file stream

    /* Ask the user for an output file name. */

    fileChooser.resetChoosableFileFilters(); // remove any existing filters
    fileChooser.setDialogTitle("Save Output As...");
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(false); // allow only one file
    if (fileChooser.showSaveDialog(mainFrame) != JFileChooser.APPROVE_OPTION)
      return;                     // user cancelled file selection dialog box

    /* Write lines to output file. */

    try                           // catch file I/O errors
    {
      output = new FileWriter(fileChooser.getSelectedFile());
                                  // try to open output file
      outputText.write(output);   // couldn't be much easier for writing!
      output.close();             // try to close output file
    }
    catch (IOException ioe)
    {
      putOutput("Can't write to output file: " + ioe.getMessage());
    }
  } // end of saveOutputText() method


/*
  showHelp() method

  Show the help summary.  This is a UNIX standard and is expected for all
  console applications, even very simple ones.
*/
  static void showHelp()
  {
    System.err.println();
    System.err.println(PROGRAM_TITLE);
    System.err.println();
    System.err.println("  java  DumpFile5  [options]  [file names]");
    System.err.println();
    System.err.println("Options:");
    System.err.println("  -? = print this help summary");
    System.err.println("  -e or -e1 = display input bytes as 8-bit ASCII text");
    System.err.println("  -e0 = display input bytes as 7-bit plain text (default)");
    System.err.println("  -w8 = show 8 input bytes per dump line");
    System.err.println("  -w16 = show 16 input bytes per dump line (default)");
    System.err.println("  -w24 = show 24 input bytes per dump line");
    System.err.println();
    System.err.println("Output may be redirected with the \">\" operator.  If no file names are given on");
    System.err.println("the command line, then a graphical interface will open.");
    System.err.println();
    System.err.println(COPYRIGHT_NOTICE);
//  System.err.println();

  } // end of showHelp() method


/*
  startBuffer() method

  Clear a line buffer to spaces, insert the file offset in hexadecimal, and put
  in the marker characters ("|") around the ASCII text.
*/
  static void startBuffer(StringBuffer buffer, long offset)
  {
    clearLine(buffer);          // clear buffer to all spaces
    putHex(buffer, 0, OFFSET_DIGITS, offset); // put file offset in hexadecimal
    buffer.setCharAt(textStart - 1, '|'); // put left side marker for text
    buffer.setCharAt(textStart + dumpWidth, '|'); // put right side marker

  } // end of startBuffer() method


/*
  userButton() method

  This method is called by our action listener actionPerformed() to process
  buttons, in the context of the main DumpFile5 class.
*/
  static void userButton(ActionEvent event)
  {
    Object source = event.getSource(); // where the event came from
    if (source == cancelButton)   // "Cancel" button
    {
      cancelOpen();               // stop opening files
    }
    else if (source == exitButton) // "Exit" button
    {
      System.exit(0);             // exit from this application
    }
    else if (source == fontNameDialog) // font name for output text area
    {
      /* We can safely assume that the font name is valid, because we obtained
      the names from getAvailableFontFamilyNames(), and the user can't edit
      this dialog field. */

      fontName = (String) fontNameDialog.getSelectedItem();
      outputText.setFont(new Font(fontName, Font.PLAIN, fontSize));
    }
    else if (source == fontSizeDialog) // point size for output text area
    {
      /* We can safely parse the point size as an integer, because we supply
      the only choices allowed, and the user can't edit this dialog field. */

      fontSize = Integer.parseInt((String) fontSizeDialog.getSelectedItem());
      outputText.setFont(new Font(fontName, Font.PLAIN, fontSize));
    }
    else if (source == openButton) // "Open Files" button
    {
      openFiles();                // select and open files
    }
    else if (source == saveButton) // "Save Output As" button
    {
      saveOutputText();           // save output text in a file
    }
    else
    {
      putOutput("Error in userButton(): ActionEvent not recognized: " + event);
    }
  } // end of userButton() method

} // end of DumpFile5 class

// ------------------------------------------------------------------------- //

/*
  DumpFile5User class

  This class listens to input from the user and passes back event parameters to
  static methods in the main class.
*/

class DumpFile5User implements ActionListener, Runnable
{
  /* empty constructor */

  public DumpFile5User() { }

  /* button listener, dialog boxes, etc */

  public void actionPerformed(ActionEvent event)
  {
    DumpFile5.userButton(event);
  }

  /* Call a separate heavy-duty processing thread in the main class. */

  public void run()
  {
    DumpFile5.openFilesRunner();
  }

} // end of DumpFile5User class

/* Copyright (c) 2007 by Keith Fenske.  Apache License or GNU GPL. */
