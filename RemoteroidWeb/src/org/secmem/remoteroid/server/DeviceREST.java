package org.secmem.remoteroid.server;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.secmem.remoteroid.server.database.Account;
import org.secmem.remoteroid.server.database.Device;
import org.secmem.remoteroid.server.exception.DeviceNotFoundException;
import org.secmem.remoteroid.server.response.BaseErrorResponse;
import org.secmem.remoteroid.server.response.BaseResponse;
import org.secmem.remoteroid.server.response.Codes;
import org.secmem.remoteroid.server.response.ObjectResponse;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;

@Path("/device")
public class DeviceREST extends DBUtils{
	
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/register")
	public BaseResponse registerDevice(Device device){
		// Check user credential first
		if(!AccountREST.isUserCredentialMatches(device.getOwnerAccount())){
			// Failed to authenticate.
			return new BaseErrorResponse(Codes.Error.Account.AUTH_FAILED);
		}
		
		// Check duplication in devices which current user has registered
		if(!isDuplicateDeviceExists(device.getOwnerAccount().getEmail(), device.getNickname())){
			return new BaseErrorResponse(Codes.Error.Device.DUPLICATE_NAME);
		}
		
		try{
			// Generate device uuid
			device.setDeviceUUID(UUID.randomUUID().toString());
			
			// If no duplication exists, save registration info into Datastore
			// Create entity for this
			Entity entity = getDeviceEntity();
			entity.setProperty(Device.OWNER_EMAIL, device.getOwnerAccount().getEmail());
			entity.setProperty(Device.NICKNAME, device.getNickname());
			entity.setProperty(Device.REGISTRATION_KEY, device.getRegistrationKey());
			entity.setProperty(Device.DEVICE_UUID, device.getUUID());
			
			// Put into datastore
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			datastore.put(entity);
			
			return new ObjectResponse<Device>(device);
		}catch(Exception e){
			e.printStackTrace();
			return new BaseErrorResponse();
		}
	}
	
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/update")
	public BaseResponse updateDevice(Device device){
		// Check user credential first
		if(!AccountREST.isUserCredentialMatches(device.getOwnerAccount())){
			// Failed to authenticate.
			return new BaseErrorResponse(Codes.Error.Account.AUTH_FAILED);
		}
		
		try{
			// Get Entity object from data using user's email and uuid
			Entity deviceEntity = getDeviceEntity(device.getOwnerAccount().getEmail(), device.getUUID());
			
			// Modify each entity's value to new one (Only supports nickname to be changed)
			deviceEntity.setProperty(Device.NICKNAME, device.getNickname());
			
			// Put entity into datastore to apply changes
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			datastore.put(deviceEntity);
			return new BaseResponse();
		}catch(Exception e){
			e.printStackTrace();
			return new BaseErrorResponse();
		}
	}
	
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/delete")
	public BaseResponse deleteDevice(Device device){
		// Check user credential first
		if(!AccountREST.isUserCredentialMatches(device.getOwnerAccount())){
			// Failed to authenticate.
			return new BaseErrorResponse(Codes.Error.Account.AUTH_FAILED);
		}
		
		try{
			// If entity exists in datastore, delete it.
			Entity deviceEntity = getDeviceEntity(device.getOwnerAccount().getEmail(), device.getUUID());
			
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			datastore.delete(deviceEntity.getKey());
			
			return new BaseResponse();
		}catch(Exception e){
			e.printStackTrace();
			return new BaseErrorResponse();
		}
	}
	
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/deleteAll")
	public static BaseResponse deleteAllDevicesOfUser(Account account){
		// Check user credential first
		if(!AccountREST.isUserCredentialMatches(account)){
			// Failed to authenticate.
			return new BaseErrorResponse(Codes.Error.Account.AUTH_FAILED);
		}
		
		try{
			// Delete all of the device data which is linked to given user's account
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Query q = new Query(Device._NAME);
			q.setFilter(new FilterPredicate(Device.OWNER_EMAIL, FilterOperator.EQUAL, account.getEmail()));
			Iterable<Entity> result = datastore.prepare(q).asIterable();
			for(Entity entity : result){
				datastore.delete(entity.getKey());
			}
			return new BaseResponse();
		}catch(Exception e){
			e.printStackTrace();
			return new BaseErrorResponse();
		}
	}
	
	private boolean isDuplicateDeviceExists(String email, String nickname){
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query q = new Query(Device._NAME);
		q.setFilter(CompositeFilterOperator.and(
				new FilterPredicate(Device.OWNER_EMAIL, FilterOperator.EQUAL, email),
				new FilterPredicate(Device.NICKNAME, FilterOperator.EQUAL, nickname)));
		return datastore.prepare(q).asList(FetchOptions.Builder.withDefaults()).size() > 1 ? true : false;
	}
	
	static Entity getDeviceEntity(String email, String deviceUUID) throws DeviceNotFoundException{
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query q = new Query(Device._NAME);
		q.setFilter(CompositeFilterOperator.and(
				new FilterPredicate(Device.OWNER_EMAIL, FilterOperator.EQUAL, email),
				new FilterPredicate(Device.DEVICE_UUID, FilterOperator.EQUAL, deviceUUID)));
		List<Entity> result = datastore.prepare(q).asList(FetchOptions.Builder.withDefaults());
		if(result.size()==0)
			throw new DeviceNotFoundException();
		else if(result.size() > 1)
			throw new IllegalStateException();
		else
			return result.get(0);
	}
	
}