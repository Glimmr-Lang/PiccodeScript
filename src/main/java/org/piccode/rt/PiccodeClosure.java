package org.piccode.rt;

import com.github.tomaslanger.chalk.Chalk;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.piccode.ast.Arg;
import org.piccode.ast.Ast;

/**
 *
 * @author hexaredecimal
 */
public class PiccodeClosure implements PiccodeValue {
	
	List<Arg> params; // Full list of args (with name and default val)
	Map<String, PiccodeValue> appliedArgs; // Applied named args
	int positionalIndex; // How many positional args have been applied so far
	Ast body;
	public Ast creator;

	public Ast.Location callSite = null;
	public String callSiteFile = null;

	public String file;
	public int line, column;
	public Integer frame = null;
	public PiccodeClosure(List<Arg> params, Map<String, PiccodeValue> appliedArgs, int positionalIndex, Ast body) {
		this.params = params == null ? List.of() : params;
		this.appliedArgs = appliedArgs;
		this.positionalIndex = positionalIndex;
		this.body = body;
	}


	public PiccodeValue call(PiccodeValue arg) {
		if (positionalIndex >= params.size()) {
			var err = new PiccodeException(callSiteFile, callSite.line, callSite.col, "Too many arguments. Expected " + params.size() + " but got " + (positionalIndex +  1));
			err.frame = frame;
			var note = new PiccodeException(file, line, column, "The function you are trying to call is declared below");
			err.addNote(note);
			throw err;
		}

		String paramName = params.get(positionalIndex).name;
		Map<String, PiccodeValue> newArgs = new HashMap<>(appliedArgs);
		newArgs.put(paramName, arg);

		var result = new PiccodeClosure(params, newArgs, positionalIndex + 1, body);
		result.creator = creator;
		result.frame = frame;
		result.callSite = callSite;
		result.callSiteFile = callSiteFile;
		result.file = file;
		result.line = line;
		result.column = column;
		return result;
	}

	public PiccodeValue callNamed(String name, PiccodeValue arg) {
		boolean paramExists = params.stream().anyMatch(p -> p.name.equals(name));
		if (!paramExists) {
			var err = new PiccodeException(callSiteFile, callSite.line, callSite.col, "Function does not have a parameter named '" + name + "'");
			err.frame = frame;
			var note = new PiccodeException(file, line, column, "The function you are trying to call is declared below");
			err.addNote(note);
			throw err;
		}

		if (positionalIndex >= params.size()) {
			var err = new PiccodeException(callSiteFile, callSite.line, callSite.col, "Too many arguments. Expected " + params.size() + " but got " + (positionalIndex + 1));
			err.frame = frame;
			var note = new PiccodeException(file, line, column, "The function you are trying to call is declared below");
			err.addNote(note);
			throw err;
		}

		if (appliedArgs.containsKey(name)) {
			var err = new PiccodeException(callSiteFile, callSite.line, callSite.col, "Duplicate argument: " + name);
			err.frame = frame;
			var note = new PiccodeException(file, line, column, "The function you are trying to call is declared below");
			err.addNote(note);
			throw err;
		}

		Map<String, PiccodeValue> newArgs = new HashMap<>(appliedArgs);
		newArgs.put(name, arg);

		var result = new PiccodeClosure(params, newArgs, positionalIndex + 1, body);
		result.creator = creator;
		result.frame = frame;
		result.callSite = callSite;
		result.callSiteFile = callSiteFile;
		result.file = file;
		result.line = line;
		result.column = column;
		return result;
	}

	public PiccodeValue evaluateIfReady() {
		for (Arg param : params) {
			boolean isSet = appliedArgs.containsKey(param.name);
			boolean hasDefault = param.def_val != null;
			if (!isSet && !hasDefault) {
				return this; // Not ready yet — required arg still missing
			}
		}

		var ctx = frame == null
			? Context.top
			: Context.getContextAt(frame);
		// All required args satisfied (either by user or by default), run
		ctx.pushStackFrame(creator);
		for (Arg param : params) {
			PiccodeValue val = appliedArgs.getOrDefault(
							param.name,
							param.def_val != null ? param.def_val.execute(frame) : null
			);
			
			if (param.export && !(val instanceof PiccodeObject)) {
				throw new PiccodeException(param.file, param.line , param.column, "Cannot export fields of a value that is not an object. Found type " + Chalk.on(val.type()).red());
			} else if (param.export && val instanceof PiccodeObject obj) {
				for (var kv : obj.obj.entrySet()) {
					var name = kv.getKey();
					var value = kv.getValue();
					ctx.putLocal(name, value);
				}
			} else {
				ctx.putLocal(param.name, val);
			}
		}

		try {
			var result = body.execute(frame);
			ctx.dropStackFrame();
			
			return result;
		} catch (PiccodeReturnException ret) {
			ctx.dropStackFrame();
			return ret.value;
		} catch (StackOverflowError se) {
			ctx.dropStackFrame();
			var err = new PiccodeException(callSiteFile, callSite.line, callSite.col, "Stack overflow");
			err.frame = frame;
			var note = new PiccodeException(file, line, column, "Stack overflow error most likely occured when you called the function below. ");
			err.addNote(note);
			throw err;
		}
	}

	@Override
	public Object raw() {
		return body;
	}

	@Override
	public String toString() {
		return "Rt.Fn/" + positionalIndex;
	}

	@Override
	public String type() {
		return "Function";
	}

}
