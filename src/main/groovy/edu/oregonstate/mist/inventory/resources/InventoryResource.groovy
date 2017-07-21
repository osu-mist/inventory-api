package edu.oregonstate.mist.inventory.resources

import com.codahale.metrics.annotation.Timed
import edu.oregonstate.mist.api.Error
import edu.oregonstate.mist.api.Resource
import edu.oregonstate.mist.api.jsonapi.ResourceObject
import edu.oregonstate.mist.api.jsonapi.ResultObject
import edu.oregonstate.mist.inventory.ErrorMessages
import edu.oregonstate.mist.inventory.core.Inventory
import edu.oregonstate.mist.inventory.db.InventoryDAOWrapper
import groovy.transform.TypeChecked
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.annotation.security.PermitAll
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import static java.util.UUID.randomUUID

@Path("inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@PermitAll
@TypeChecked
class InventoryResource extends Resource {

    Logger logger = LoggerFactory.getLogger(InventoryResource.class)

    private InventoryDAOWrapper inventoryDAOWrapper
    private URI endpointUri

    //used to check if client-generated id is a valid UUID
    private final String uuidRegEx =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[34][0-9a-fA-F]{3}-[89ab][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"

    InventoryResource(InventoryDAOWrapper inventoryDAOWrapper) {
        this.inventoryDAOWrapper = inventoryDAOWrapper
    }

    /**
     * Get all inventory objects that aren't deleted
     * @return
     */
    @Timed
    @GET
    Response getAllInventories() {
        List<ResourceObject> inventories = inventoryDAOWrapper.getAllInventories()

        ok(new ResultObject(data: inventories)).build()
    }

    /**
     * Get one inventory object by ID
     * @param inventoryID
     * @return
     */
    @Timed
    @GET
    @Path('{id: [0-9a-zA-Z-]+}')
    Response getInventoryByID(@PathParam("id") String inventoryID) {
        ResourceObject inventory = inventoryDAOWrapper.getInventoryById(inventoryID)

        if (!inventory) {
            return notFound().build()
        }

        ok(new ResultObject(data: inventory)).build()
    }

    @Timed
    @POST
    Response createInventory(@Valid ResultObject newResultObject) {
        List<Error> errors = getErrors(newResultObject)

        if (errors) {
            Response.ResponseBuilder responseBuilder = Response.status(Response.Status.BAD_REQUEST)
            return responseBuilder.entity(errors).build()
        }
        Inventory newInventory = resultObjectToInventory(newResultObject)
        newInventory.id = newResultObject.data['id'] ?: randomUUID() as String

        inventoryDAOWrapper.createInventory(newInventory)

        ResultObject newCreatedInventory = new ResultObject(
                data: (inventoryDAOWrapper.getInventoryById(newInventory.id))
        )

        created(newCreatedInventory).build()
    }

    /**
     * Delete one inventory object by ID
     * @param inventoryID
     * @return
     */
    @Timed
    @DELETE
    @Path('{id: [0-9a-zA-Z-]+}')
    Response deleteInventoryByID(@PathParam("id") String inventoryID) {
        ResourceObject inventory = inventoryDAOWrapper.getInventoryById(inventoryID)
        Response response

        if (!inventory) {
            response = notFound().build()
        } else {
            try {
                inventoryDAOWrapper.deleteInventory(inventoryID)
                response = Response.noContent().build()
            } catch (Exception e) {
                logger.error("Error deleting inventory record or associated records.", e)
                response = internalServerError(
                        "There was a problem when deleting the inventory record.")
                        .build()
            }
        }

        response
    }

    private Inventory resultObjectToInventory(ResultObject resultObject) {
        (Inventory) resultObject.data['attributes']
    }

    private List<Error> getErrors(ResultObject resultObject) {
        List<Error> errors = []
        ResourceObject resourceObject
        Inventory inventory

        // Invalid UUID
        if (resultObject.data["id"]) {
            String id = resultObject.data["id"]

            if (!id.matches(uuidRegEx)) {
                errors.add(Error.badRequest(ErrorMessages.invalidUUID))
            }

            if (inventoryDAOWrapper.getInventoryById(id)) {
                errors.add(Error.badRequest(ErrorMessages.idExists))
            }
        }

        // Try casting resultObject as Inventory object.
        try {
            resultObjectToInventory(resultObject)
        } catch (GroovyCastException e) {
            errors.add(Error.badRequest(ErrorMessages.castError))

            // If it can't cast the ResultObject as an Inventory object, return.
            return errors
        }

        // Type is either API, Talend, or Other
        // If Type is Other, otherType may not be null
        // Check valid attributes
        // Check valid query params
        // Check valid consuming entities
        // Check valid provided data

        errors
    }
}