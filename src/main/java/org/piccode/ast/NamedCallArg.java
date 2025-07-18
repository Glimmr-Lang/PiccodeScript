package org.piccode.ast;

import org.piccode.piccodescript.TargetEnvironment;
import org.piccode.rt.PiccodeValue;

/**
 *
 * @author hexaredecimal
 */
public class NamedCallArg extends Ast {
	public String name;
	public Ast value;

	public NamedCallArg(String name, Ast def_val) {
		this.name = name;
		this.value = def_val;
	}

	@Override
	public String toString() {
		if (value == null) {
			return name;
		}
		return name  + "=" + value;
	}

	@Override
	public PiccodeValue execute(Integer frame) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}

	@Override
	public String codeGen(TargetEnvironment target) {
		throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
	}
	
}
