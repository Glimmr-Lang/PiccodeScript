package org.piccode.ast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.piccode.piccodescript.TargetEnvironment;
import org.piccode.rt.Context;
import org.piccode.rt.PiccodeModule;
import org.piccode.rt.PiccodeValue;

/**
 *
 * @author hexaredecimal
 */
public class ModuleAst extends Ast {

	public String name;
	public List<Ast> nodes;
	public boolean createSymbol;

	public ModuleAst(String name, List<Ast> nodes) {
		this.name = name;
		this.nodes = nodes;
		this.createSymbol = true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("module ");
		sb.append(name).append(" {\n");
		for (var node : nodes) {
			sb.append(node.toString().indent(4));
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public PiccodeValue execute(Integer frame) {
		var ctx = Context.top;
		var module = ctx.getValue(name);

		processNodes();

		if ((module == null) || !(module instanceof PiccodeModule)) {
			var mod = new PiccodeModule(name, nodes);
			if (createSymbol) {
				ctx.putLocal(name, mod);
			}
			return mod;
		}

		var mod = (PiccodeModule) module;
		mod.nodes.addAll(nodes);
		return mod;
	}

	@Override
	public String codeGen(TargetEnvironment target) {
		var sb = new StringBuilder();
		sb.append("var ")
						.append(name)
						.append(" = {\n");

		int exprs = 0;
		for (var node : nodes) {
			if (node instanceof FunctionAst func) {
				sb
								.append(func.name)
								.append(":")
								.append(func.codeGen(target));
				continue;
			}

			if (node instanceof VarDecl vardel) {
				sb
								.append(vardel.name)
								.append(":")
								.append(vardel.codeGen(target));
				continue;
			}

			sb
							.append(String.format("_%s:", exprs++))
							.append(node.codeGen(target));
		}

		sb.append("}\n");
		return sb.toString();
	}

	private void processNodes() {
		HashMap<String, FunctionAst> funcs = new HashMap<>();
		List<Ast> newNodes = new ArrayList<>();
		for (var node : nodes) {
			if (node instanceof FunctionAst func) {
				if (funcs.containsKey(func.name)) {
					var fx = funcs.get(func.name);
					var clause = new ClauseAst(func.arg, func.body);
					fx.clauses.add(clause);
					funcs.put(func.name, fx);
				} else {
					funcs.put(func.name, func);
				}
			} else {
				newNodes.add(node);
			}
		}

		if (funcs.isEmpty()) {
			return;
		}

		for (var fx : funcs.values()) {
			newNodes.add(fx);
		}
		nodes = newNodes;
	}

}
