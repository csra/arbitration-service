/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.util;

import rsb.Scope;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class ScopeParser implements StringParser<Scope> {

	@Override
	public Scope getValue(String tgt) throws IllegalArgumentException {
		try {
			Scope s = new Scope(tgt);
			return s;
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("invalid scope.", ex);
		}
	}

	@Override
	public Class<Scope> getTargetClass() {
		return Scope.class;
	}
}
