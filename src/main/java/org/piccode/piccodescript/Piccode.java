package org.piccode.piccodescript;

import com.github.tomaslanger.chalk.Chalk;
import com.github.tomaslanger.cli.choice.SingleChoice;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.midi.SysexMessage;
import javax.xml.crypto.Data;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.piccode.backend.Compiler;
import org.piccode.rt.PiccodeString;
import org.piccode.rt.PiccodeValue;

import org.jline.reader.*;
import org.jline.reader.impl.*;
import org.jline.terminal.*;
import org.jline.terminal.impl.*;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.piccode.ast.IdentifierAst;
import org.piccode.platf.Platforms;
import org.piccode.rt.Context;
import org.piccode.rt.PiccodeException;
import org.piccode.rt.ReplState;

/**
 *
 * @author hexaredecimal
 */
public class Piccode {

	private static double VERSION = 1.0;

	public static void main(String[] args) {
		if (Platforms.isWindows()) {
			Platforms.setWindowsCodePage();
		}
		run(args);
	}

	private static void run(String[] args) {
		var parser = ArgumentParsers.newFor("piccodescript").build()
						.defaultHelp(true)
						.version(String.format("${prog} %s", VERSION))
						.epilog(about())
						.description("The compiler for the PiccodeScript programming language");

		var subparsers = parser.addSubparsers()
						.title("subcommands")
						.metavar("COMMAND")
						.dest("command");
		
		var new_proj = subparsers.addParser("new")
						.defaultHelp(true)
						.help("Create a new project");
		
		new_proj.addArgument("name")
						.nargs("?") // Optional
						.help("The name of the project");

		// glmr build
		var build = subparsers.addParser("build")
						.defaultHelp(true)
						.help("Compile the project");

		build.addArgument("--emit")
						.action(storeTrue())
						.help("Print the generated JavaScript to stdout");

		build.addArgument("--target")
						.setDefault(TargetEnvironment.JS)
						.type(TargetEnvironment.class)
						.choices(TargetEnvironment.JS, TargetEnvironment.Eval, TargetEnvironment.Ast)
						.help("Specify the target environment to build for");

		build.addArgument("--outfile")
						.setDefault("./target/build.js")
						.help("Set the output file path");

		build.addArgument("file")
						.nargs("?") // Optional
						.help("Path to a specific source file to build");

		// glmr dump
		var dump = subparsers.addParser("dump")
						.defaultHelp(true)
						.help("Prints internal IR and other structures");

		dump.addArgument("tc")
						.nargs("?")
						.help("Print functions after typecheck");

		// glmr run
		var run = subparsers.addParser("run")
						.defaultHelp(true)
						.help("Execute after builing");

		run.addArgument("--repl")
						.dest("repl")
						.setDefault(false)
						.action(Arguments.storeTrue())
						.help("Launch the REPL instead of executing a file");

		run.addArgument("file")
						.nargs("?") // Optional
						.help("Path to a specific source file to build");

		run.addArgument("--")
						.nargs("*")
						.dest("extra_args")
						.help("Pass-through args");

		var lib = subparsers.addParser("lib")
						.defaultHelp(true)
						.help("Build sources into a lib");

		lib.addArgument("--new")
						.action(storeTrue())
						.help("Create a new jar file");
		
		lib.addArgument("--ls")
						.action(storeTrue())
						.help("Lists the contents of the jar file");

		lib.addArgument("file")
						.nargs("?") // Optional
						.help("The directory to build a lib from or the lib to work with");
		
		parser.addArgument("--version").action(Arguments.version());

		// optional: glmr help
		var help = subparsers.addParser("help")
						.help("Show help for a subcommand")
						.defaultHelp(false);

		help.addArgument("topic")
						.help("The topic or subcommand to show help for");

		try {
			Namespace res = parser.parseArgs(args);
			String command = res.getString("command");

			if ("new".equals(command)) {
				var name = res.getString("name");
				if (name == null) {
					createNewProject();
					return;
				}
			}

			if ("lib".equals(command)) {
				var input = res.getString("file");
				if (input == null) {
					System.out.println("No input directory or file");
					return;
				}

				if (res.getBoolean("new")) {
					createJarFile(input);
					return;
				}

				if (res.getBoolean("ls")) {
					if (!input.endsWith(".jar")) {
						System.out.println("Invalid library archive file");
						return;
					}

					printLib(input);
					return;
				}
				
				
			}

			if ("build".equals(command)) {
				/*System.out.println("==> build:");
				System.out.println("Emit: " + res.getBoolean("emit"));
				System.out.println("Target: " + res.getString("target"));
				System.out.println("Outfile: " + res.getString("outfile"));
				 */

				if (res.getBoolean("emit")) {
					var env = TargetEnvironment.valueOf(res.getString("target"));
					String input = res.getString("file");
					if (input == null && isProject()) {
						System.out.println("Project run");
						return;
					} else if (input == null) {
						System.out.println("No input file to run");
						return;
					}
					
					if (env == TargetEnvironment.Ast) {
						var code = readFile(input);
						Compiler
										.parse(input, code)
										.forEach(System.out::println);
					}

					if (env == TargetEnvironment.JS) {
						var code = readFile(input);
						var out = Compiler
										.program(code, code).codeGen(env);
						System.out.println(out);
					}
				}
				return;
			} else if ("dump".equals(command)) {
				System.out.println("==> dump:");
				System.out.println("TC arg: " + res.getString("tc"));
			} else if ("run".equals(command)) {
				List<PiccodeValue> user_args = makeUserArgs(res.getList("extra_args"));
				String input = res.getString("file");
				boolean repl = res.getBoolean("repl");
				if (repl) {
					repl(user_args);
				} else {
					if (input == null && isProject()) {
						System.out.println("Project run");
						return;
					} else if (input == null) {
						System.out.println("No input file to run");
						return;
					}
					var code = readFile(input);
					Compiler.compile(input, code, user_args);
				}
			} else if ("help".equals(command)) {
				String topic = res.getString("topic");
				parser.parseArgs(new String[]{topic, "--help"});
			}
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
	}

	public static String readFile(String file) {
		try {
			return Files.readString(new File(file).toPath());
		} catch (IOException ex) {
			System.out.println("" + ex.getMessage());
			System.exit(1);
		}
		return null;
	}

	private static String about() {
		return String.format("""
▄▖▘       ▌  ▄▖    ▘  ▗ 
▙▌▌▛▘▛▘▛▌▛▌█▌▚ ▛▘▛▘▌▛▌▜▘
▌ ▌▙▖▙▖▙▌▙▌▙▖▄▌▙▖▌ ▌▙▌▐▖
                    ▌   
       [%s]
""", Chalk.on("v" + VERSION).green()// TODO: Add the git commit and branch
	).indent(8);
	}

	private static boolean isProject() {
		var fp = new File("./project.toml");
		return fp.exists();
	}

	private static void repl(List<PiccodeValue> user_args) {
		var fmt = String.format(
				"Welcome to the REPL for PiccodeScript" 
				+ Chalk.on(" v%s.").green() 
				+ " Running on " 
				+ Chalk.on("%s").blue()
				+ " "
				+ Chalk.on("%s").gray(), 
				VERSION, 
				Platforms.getName(),
				Platforms.getArch());

		var bands = Chalk.on("| ").yellow();
		System.out.println();
		System.out.println(bands);
		System.out.println(bands + fmt);
		System.out.println(bands);
		System.out.println();
		// Create a terminal
		try (Terminal terminal = TerminalBuilder.builder()
						.system(true)
						.build();) {


			var piccodeScriptHighlighter = new Highlighter() {
				// Pattern to match SQL keywords (case insensitive)
				private final Pattern PICCODE_KEYWORDS = Pattern.compile(
								"\\b(module|when|is|if|else|import)\\b");

				@Override
				public AttributedString highlight(LineReader reader, String buffer) {
					AttributedStringBuilder builder = new AttributedStringBuilder();

					Matcher matcher = PICCODE_KEYWORDS.matcher(buffer);
					int lastEnd = 0;

					while (matcher.find()) {
						// Add text before the keyword with default style
						builder.append(buffer.substring(lastEnd, matcher.start()));

						// Add the keyword with bold blue style
						builder.styled(
										AttributedStyle.BOLD.foreground(AttributedStyle.BLUE),
										buffer.substring(matcher.start(), matcher.end()));

						lastEnd = matcher.end();
					}

					// Add any remaining text
					if (lastEnd < buffer.length()) {
						builder.append(buffer.substring(lastEnd));
					}

					return builder.toAttributedString();
				}
			};


			
			LineReader reader = LineReaderBuilder.builder()
							.terminal(terminal)
							.appName("PiccodeScript REPL")
							.highlighter(piccodeScriptHighlighter)
							.build();

			var prompt = new AttributedString("λ ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));
			var inner_prompt = new AttributedString(">>> ", AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN));

			var is_inner = false;
			ReplState.ACTIVE = true;

			Compiler.prepareGlobalScope("repl", "REPL");
			outer:
			while (true) {
				StringBuilder inputBlock = new StringBuilder();
				while (true) {
					String line = reader.readLine(is_inner ? inner_prompt.toAnsi() : prompt.toAnsi());
					if (line.trim().isEmpty()) {
						is_inner = false;
						break; // empty or just whitespace — end of block
					}


					var last = line.charAt(line.length() - 1);
					inputBlock.append(line).append("\n");
					if (last != '{'){
						if (is_inner && last != '}') {
							continue;
						}
						is_inner = false;
						break;
					} 
					is_inner = true;
				}
				String code = inputBlock.toString();
				ReplState.CODE = code;
				if ("exit".equalsIgnoreCase(code.trim())) {
					break outer;
				}
				if (code.trim().isEmpty()) {
					continue;
				}

				var result = Compiler.program("repl", code);
				PiccodeValue res = null;
				try {
					for (var stmt : result.nodes) {
						res = stmt.execute(null);
					}

					terminal.writer().println(res + " : " + res.type());
					terminal.flush();
				} catch (PiccodeException e) {
					e.reportError();
					var stack_size = Context.top.getFramesCount();
					if (stack_size > 1) {
						Context.top.dropStackFrame();
					}
				}
			}

			Context.top.dropStackFrame();
			ReplState.ACTIVE = false;
			terminal.writer().println("Exiting REPL");
		} catch (IOException | EndOfFileException | UserInterruptException e) {
			ReplState.ACTIVE = false;
			System.out.println("Exiting REPL");
		}
	}

	private static List<PiccodeValue> makeUserArgs(List<Object> list) {
		var args = new ArrayList<PiccodeValue>();
		list.forEach(item -> args.add(new PiccodeString(item.toString())));
		return args;
	}

	private static void createNewProject() {
		try (Terminal terminal = TerminalBuilder.builder()
						.system(true)
						.build();) {
			LineReader reader = LineReaderBuilder.builder()
							.terminal(terminal)
							.appName("PiccodeScript Project Manager")
							.build();

					var name = reader.readLine(Chalk.on("Project Name: ").green().toString());
					System.out.print(Chalk.on("Which target are you building for? ").green());
					System.out.println(Chalk.on("(Default: Eval)").gray());
					var choice = SingleChoice.Builder.singleChoice();
					var target = choice.select(
						TargetEnvironment.Eval.toString(),
						TargetEnvironment.Ast.toString(),
						TargetEnvironment.Eval.toString(),
						TargetEnvironment.Ir.toString(),
						TargetEnvironment.JS.toString()
					);
					var author = reader.readLine(Chalk.on("Project Author: ").green().toString());
					System.out.print(Chalk.on("Which license to add to your project? ").green());
					System.out.println(Chalk.on("(Default: MIT)").gray());
					var license = choice.select("MIT", "MIT", "GPL", "None");

					System.out.println("Creating project: " + Chalk.on(name).green());
          // TODO: Create project structure and build script
          //
					System.out.println("Project " + Chalk.on(name).green() + " has been created successfully");
          System.out.println(Chalk.on("[NOTE]: ").yellow() + " run " + Chalk.on("piccodescript build").green() + " to build the project for the selected target");
          System.out.println(Chalk.on("[NOTE]: ").yellow() + " run " + Chalk.on("piccodescript run").green() + " to run the project");
    } catch (Exception ex) {
			System.out.println("Cancelled by user");
			return;
		}
	}

	private static void createJarFile(String input) {
		var inputFile = new File(input);
		var outputFile = new File(inputFile.getName() + ".jar");

		String baseName = inputFile.getName();


		// Create custom manifest
		Manifest manifest = new Manifest();
		var attr = manifest.getMainAttributes();
		attr.putValue("Manifest-Version", "1.0");
		attr.putValue("PiccodeScript-Version", "" + VERSION);
		attr.putValue("Library-Name", inputFile.getName());
		attr.putValue("Library-Type", "PiccodeScript-Native");
		attr.putValue("Build-Date", new Date().toString());

		try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outputFile), manifest)) {
			addToJar(inputFile, baseName ,jos);
		} catch (FileNotFoundException ex) {
		} catch (IOException ex) {
		}

		System.out.println("JAR with custom manifest created!");
	}

	
	private static void addToJar(File file, String baseName, JarOutputStream jos) throws IOException {
		String entryName = baseName + file.getPath()
						.substring(new File(baseName).getPath().length()) // relative to base
						.replace("\\", "/");

		if (file.isDirectory()) {
			if (!entryName.endsWith("/")) {
				entryName += "/";
			}
			JarEntry dirEntry = new JarEntry(entryName);
			jos.putNextEntry(dirEntry);
			jos.closeEntry();

			for (File child : file.listFiles()) {
				addToJar(child, baseName, jos);
			}
		} else {
			JarEntry fileEntry = new JarEntry(entryName);
			jos.putNextEntry(fileEntry);
			try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
				byte[] buffer = new byte[1024];
				int count;
				while ((count = in.read(buffer)) != -1) {
					jos.write(buffer, 0, count);
				}
			}
			jos.closeEntry();
		}
	}


	
	private static void printLib(String path) {
		File jarPath = new File(path);
		try (JarFile jarFile = new JarFile(jarPath)) {

			// Print manifest entries
			Manifest manifest = jarFile.getManifest();
			System.out.println("Manifest contents:");
			manifest.getMainAttributes().forEach((k, v) -> System.out.println(k + ": " + v));

			// Print files in jar
			System.out.println("\nSources:");
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				System.out.println(" - " + entry.getName());
			}
		} catch (IOException ex) {
			System.getLogger(Piccode.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
		}
	}
}
