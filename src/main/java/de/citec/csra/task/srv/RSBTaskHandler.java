/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.task.srv;

import rsb.RSBException;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public interface RSBTaskHandler {
	
	public void activate() throws RSBException, InterruptedException;
	public void deactivate() throws RSBException, InterruptedException;
	
}
