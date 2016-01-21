/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.util;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class EnumParser<T extends Enum<T>> implements StringParser<T> {

	private final Class<T> cls;
	
	public EnumParser(Class<T> cls) {
		this.cls = cls;
	}

	@Override
	public T getValue(String val) throws IllegalArgumentException {
		for(T e : cls.getEnumConstants()){
			if(e.name().equalsIgnoreCase(val)){
				return e;
			}
		}
		throw new IllegalArgumentException("No enum constant " + cls.getCanonicalName() + "." + val);
	}
}
