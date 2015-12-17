/*
 * $Id$
 * $URL$
 */
package com.googlecode.jarzilla;

import ch.randelshofer.quaqua.QuaquaManager;
import com.apple.eawt.AppEvent.OpenFilesEvent;
import com.apple.eawt.AppEvent.QuitEvent;
import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;
import com.apple.eawt.OpenFilesHandler;
import com.apple.eawt.QuitHandler;
import com.apple.eawt.QuitResponse;
import com.explodingpixels.macwidgets.BottomBarSize;
import com.explodingpixels.macwidgets.LabeledComponentGroup;
import com.explodingpixels.macwidgets.MacButtonFactory;
import com.explodingpixels.macwidgets.MacUtils;
import com.explodingpixels.macwidgets.UnifiedToolBar;
import com.googlecode.jarzilla.core.ArchiveFile;
import com.googlecode.jarzilla.core.ArchiveFileEntry;
import com.googlecode.jarzilla.core.Utils;
import com.googlecode.jarzilla.ui.DetailsMessageBox;
import com.googlecode.jarzilla.ui.HelpDialog;
import com.googlecode.jarzilla.ui.JarzillaBottomBar;
import com.googlecode.jarzilla.ui.ResultsPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Jarzilla application class
 *
 * @author rayvanderborght
 */
@SuppressWarnings("deprecation")
public class Jarzilla
{
	private static final String APP_NAME = "Jarzilla";

	public static Jarzilla jarzilla;
	public static boolean stop = false;
	private boolean firstSearch = true;
	private ArchiveFile archiveFile;

	private final Application app = Application.getApplication();
	private final ResultsPanel resultsPanel;
	private final JTextField searchField;
	private final JFileChooser fileChooser;
	private final JarzillaBottomBar bottomBar;

	/** */
	private static JFrame frame;
	public static JFrame getFrame() { return frame; }

	static
	{
		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty("Quaqua.tabLayoutPolicy", "wrap");
	}

	/** */
	@SuppressWarnings("serial")
	public Jarzilla()
	{
		try
		{
			UIManager.setLookAndFeel(QuaquaManager.getLookAndFeelClassName());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		Jarzilla.frame = new JFrame();
		Jarzilla.frame.setTitle(APP_NAME);

		this.fileChooser = new JFileChooser();
		this.resultsPanel = new ResultsPanel(this);

		this.bottomBar = new JarzillaBottomBar(BottomBarSize.LARGE);
		this.bottomBar.installWindowDraggerOnWindow(frame);

		MacUtils.makeWindowLeopardStyle(frame.getRootPane());

		AbstractButton folderButton = MacButtonFactory.makeUnifiedToolBarButton(
				new JButton("Open", new ImageIcon(Jarzilla.class.getResource("/com/googlecode/jarzilla/folder.png"))));

		AbstractButton runButton = MacButtonFactory.makeUnifiedToolBarButton(
				new JButton("Run", new ImageIcon(Jarzilla.class.getResource("/com/googlecode/jarzilla/start-here.png"))));

		AbstractButton helpButton = MacButtonFactory.makeUnifiedToolBarButton(
				new JButton("Help", new ImageIcon(Jarzilla.class.getResource("/com/googlecode/jarzilla/help-browser.png"))));

		folderButton.setActionCommand("pressed");
		folderButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if ("pressed".equals(e.getActionCommand()))
				{
					int returnValue = fileChooser.showOpenDialog(null);
					if (returnValue == JFileChooser.APPROVE_OPTION)
					{
						Jarzilla.this.scanPath(fileChooser.getSelectedFile());
					}
				}
			}
		});

		runButton.setActionCommand("pressed");
		runButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				if ("pressed".equals(e.getActionCommand()))
				{
					try
					{
						String jarName = Jarzilla.this.archiveFile.getArchiveFilePath();
						System.out.println("running " + jarName);
						new ProcessBuilder("java", "-jar", jarName).start();
					}
					catch (IOException e1)
					{
						e1.printStackTrace();
					}
				}
			}
		});

		helpButton.setActionCommand("pressed");
		helpButton.addActionListener(new ActionListener()
		{
			private HelpDialog helpDialog = new HelpDialog();

			@Override
			public void actionPerformed(ActionEvent e)
			{
				if ("pressed".equals(e.getActionCommand()))
				{
					helpDialog.setVisible(true);
				}
			}
		});

		UnifiedToolBar toolBar = new UnifiedToolBar();
		toolBar.addComponentToLeft(folderButton);
		toolBar.addComponentToLeft(runButton);
		toolBar.addComponentToLeft(helpButton);
		toolBar.installWindowDraggerOnWindow(frame);

		searchField = new JTextField(10);
		searchField.putClientProperty("JTextField.variant", "search");
		searchField.putClientProperty("JTextField.Search.CancelAction", new AbstractAction("ClearSearch")
		{
			public void actionPerformed(ActionEvent event)
			{
				searchField.setText("");
				Jarzilla.this.search();
			}
		});

		toolBar.addComponentToRight(new LabeledComponentGroup("Search", searchField).getComponent());

		this.addActionListeners();
		this.buildMenu();

		frame.add(toolBar.getComponent(), BorderLayout.NORTH);
		frame.add(bottomBar.getComponent(), BorderLayout.SOUTH);
		frame.add(resultsPanel, BorderLayout.CENTER);
		frame.setSize(450, 500);
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);

		try
		{
			app.setOpenFileHandler(new OpenFilesHandler()
			{
				@Override
				public void openFiles(OpenFilesEvent event)
				{
					File file = event.getFiles().get(0);
					Jarzilla.this.scanPath(file);
				}
			});
			app.setQuitHandler(new QuitHandler()
			{
				@Override
				public void handleQuitRequestWith(QuitEvent event, QuitResponse response)
				{
					System.exit(0);
				}
			});
		}
		catch (Exception e)
		{
			// assuming this will make things backwards compatible with older
			// versions.. aka the above api was added in Java for Mac OS X 10.6
			// Update 3, Java for Mac OS X 10.5 Update 8
			app.addApplicationListener(new ApplicationAdapter()
			{
				@Override
				public void handleOpenFile(ApplicationEvent event)
				{
					File file = new File(event.getFilename());
					Jarzilla.this.scanPath(file);
				}

				@Override
				public void handleQuit(ApplicationEvent event)
				{
					System.exit(0);
				}
			});
		}
	}

	/** */
	private void buildMenu()
	{
		JMenu fileMenu = new JMenu("File");
		JMenuItem loadMI = new JMenuItem("Open");
		loadMI.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				int returnValue = fileChooser.showOpenDialog(null);
				if (returnValue == JFileChooser.APPROVE_OPTION)
				{
					Jarzilla.this.scanPath(fileChooser.getSelectedFile());
				}
			}
		});

		fileMenu.add(loadMI);
		JMenuBar mb = new JMenuBar();
		mb.add(fileMenu);
		Jarzilla.frame.setJMenuBar(mb);
	}

	/** */
	private void addActionListeners()
	{
		searchField.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent event)
			{
				if (firstSearch)
				{
					firstSearch = false;
					searchField.setText("");
				}
			}
		});
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				Jarzilla.this.search();
			}
		});
	}

	/** */
	private void showArchiveContent(String archiveFile)
	{
		List<ArchiveFileEntry> entries = this.archiveFile.getEntries();
		resultsPanel.setResults(entries);
		bottomBar.setItemCount(entries.size());
	}

	/** */
	private void init()
	{
		stop = false;
		this.clean();
	}

	/** */
	private void clean()
	{
		resultsPanel.clean();
		this.archiveFile = new ArchiveFile(null);
		Jarzilla.frame.setTitle(APP_NAME);
	}

	/**
	 * This is where indexing is done.
	 * @param f root directory of the directory tree to be indexed.
	 */
	public void scanPath(final File f)
	{
		String treeRoot;
		try
		{
			treeRoot = f.getCanonicalPath();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		this.init();

		if (!f.exists())
		{
			throw new RuntimeException("Path: '" + treeRoot + "' does not exist");
		}

		final String treeRoot1 = treeRoot;
		Runnable r = new Runnable()
		{
			public void run()
			{
				Jarzilla.frame.setCursor(new Cursor(Cursor.WAIT_CURSOR));

				Jarzilla.this.resultsPanel.setBusy(true);
				try
				{
					List<String> jarNameList = new ArrayList<String>();
					if (f.isFile())
					{
						String name = f.getCanonicalPath();
						jarNameList.add(name);
						Jarzilla.this.indexJarFile(name);
						Jarzilla.this.showArchiveContent(name);
						Jarzilla.frame.getRootPane().putClientProperty("Window.documentFile", f);
					}
					else
					{
						Jarzilla.this.scanDirectory(f, jarNameList);
					}
				}
				catch (Exception e)
				{
					Jarzilla.messageBoxWithDetails("Error:", e.getMessage(), Utils.getStackTrace(e), JOptionPane.ERROR_MESSAGE);
					e.printStackTrace();
				}
				finally
				{
					Jarzilla.this.resultsPanel.setBusy(false);
					Jarzilla.frame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
					Jarzilla.frame.setTitle(APP_NAME + " - " + treeRoot1);
					Jarzilla.frame.repaint();
				}
			}
		};

		new Thread(r, "Parsing Thread").start();

		searchField.setText("");
	}

	/** */
	private void indexJarFile(String canonicalPath) throws IOException
	{
		FileInputStream fin = new FileInputStream(canonicalPath);
		ZipInputStream jin = new ZipInputStream(fin);

		ArchiveFile archiveFile = new ArchiveFile(canonicalPath);
		for (ZipEntry entryName = jin.getNextEntry(); entryName != null; entryName = jin.getNextEntry())
		{
			if (!entryName.isDirectory())
			{
				ArchiveFileEntry entry = new ArchiveFileEntry(canonicalPath, entryName.getName(), entryName.getTime(), entryName.getSize());
				archiveFile.add(entry);
				this.archiveFile = archiveFile;
			}
		}
		fin.close();
	}

	/** */
	private void scanDirectory(File f, List<String> jarNameList) throws IOException
	{
		File[] children = f.listFiles();

		for (int i = 0; i < children.length; i++)
		{
			if (stop)
			{
				this.clean();
				return;
			}

			File file = children[i];
			if (file.isFile() && (
					file.getName().endsWith("ear") ||
					file.getName().endsWith("jar") ||
					file.getName().endsWith("sar") ||
					file.getName().endsWith("war") ||
					file.getName().endsWith("zip")))
			{
				String name = children[i].getCanonicalPath();
				jarNameList.add(name);

				this.resultsPanel.setBusy(true);
				this.indexJarFile(name);
				this.resultsPanel.setBusy(false);
			}
			else if (children[i].isDirectory())
			{
				this.scanDirectory(children[i], jarNameList);
			}
			else
			{
				// ignore
			}
		}
	}

	/** */
	private void search()
	{
		Integer found = null;
		try
		{
			frame.setCursor(new Cursor(Cursor.WAIT_CURSOR));

			this.resultsPanel.setBusy(true);

			List<ArchiveFileEntry> results = (this.archiveFile == null)
					? Collections.<ArchiveFileEntry>emptyList()
					: this.archiveFile.search(searchField.getText());

			found = results.size();
			this.resultsPanel.setResults(results);
		}
		catch (Throwable e)
		{
			Jarzilla.messageBoxWithDetails("Error:", e.getMessage(), Utils.getStackTrace(e), JOptionPane.ERROR_MESSAGE);
		}
		finally
		{
			Jarzilla.frame.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			this.bottomBar.setItemCount(found);
			this.resultsPanel.setBusy(false);
		}
	}

	/**
	 * Displays a message box with "OK" button and "Details" button.
	 * The latter opens up a bottom pane with details text in it
	 *
	 * @param title - title of message box
	 * @param message - main message
	 * @param details -  details text to be displayed in the bottom pane
	 * @param type - type of icon to display on the dialog. Possible options:
	 * <code>
	 * javax.swing.JOptionPane.WARNING_MESSAGE
	 * javax.swing.JOptionPane.ERROR_MESSAGE
	 * javax.swing.JOptionPane.INFORMATION_MESSAGE
	 * </code>
	 */
	public static void messageBoxWithDetails(String title, String message, String details, int type)
	{
		DetailsMessageBox mb = new DetailsMessageBox(Jarzilla.frame, title, message, details, type);
		mb.setVisible(true);
	}

	/** */
	public static void main(String[] args)
	{
		jarzilla = new Jarzilla();
	}
}
