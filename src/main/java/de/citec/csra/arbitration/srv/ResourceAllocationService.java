/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.srv;

import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class ResourceAllocationService {

	private ResourceAllocationService() {}
	private static ResourceAllocationService instance;
	public static ResourceAllocationService getInstance(){
		if(instance == null){
			instance = new ResourceAllocationService();
		}
		return instance;
	}
	
	public boolean allocate(ResourceAllocation res){
		
		return true;
	}
}
