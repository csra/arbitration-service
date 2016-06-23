/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation.srv;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class Allocator {
	public static void main(String[] args) throws Exception {
		AllocationServer a = AllocationServer.getInstance();
		a.activate();
		a.waitForShutdown();
		a.deactivate();
	}
}
