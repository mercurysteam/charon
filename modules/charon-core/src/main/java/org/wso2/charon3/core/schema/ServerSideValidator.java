/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.charon3.core.schema;

import org.wso2.charon3.core.attributes.SimpleAttribute;
import org.wso2.charon3.core.exceptions.BadRequestException;
import org.wso2.charon3.core.exceptions.CharonException;
import org.wso2.charon3.core.exceptions.NotFoundException;
import org.wso2.charon3.core.objects.AbstractSCIMObject;
import org.wso2.charon3.core.objects.Role;
import org.wso2.charon3.core.objects.User;
import org.wso2.charon3.core.protocol.endpoints.AbstractResourceManager;
import org.wso2.charon3.core.utils.AttributeUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Server Side Validator.
 */
public class ServerSideValidator extends AbstractValidator {

    /*
     * Validate created SCIMObject according to the spec
     *
     * @param scimObject
     * @param resourceSchema
     * @throw CharonException
     * @throw BadRequestException
     * @throw NotFoundException
     */
    public static void validateCreatedSCIMObject(AbstractSCIMObject scimObject, SCIMResourceTypeSchema resourceSchema)
            throws CharonException, BadRequestException, NotFoundException {

        if (scimObject instanceof User) {
            //set display names for complex multivalued attributes
            setDisplayNameInComplexMultiValuedAttributes(scimObject, resourceSchema);
        }
        //remove any read only attributes
        removeAnyReadOnlyAttributes(scimObject, resourceSchema);

        if (!(scimObject instanceof Role)) {
            String id = UUID.randomUUID().toString();
            scimObject.setId(id);
            Instant now = Instant.now();
            // Set the created date and time.
            scimObject.setCreatedInstant(AttributeUtil.parseDateTime(AttributeUtil.formatDateTime(now)));
            // Creates date and the last modified are the same if not updated.
            scimObject.setLastModifiedInstant(AttributeUtil.parseDateTime(AttributeUtil.formatDateTime(now)));
        }
        //set location and resourceType
        if (resourceSchema.isSchemaAvailable(SCIMConstants.USER_CORE_SCHEMA_URI)) {
            String location = createLocationHeader(AbstractResourceManager.getResourceEndpointURL(
                    SCIMConstants.USER_ENDPOINT), scimObject.getId());
            scimObject.setLocation(location);
            scimObject.setResourceType(SCIMConstants.USER);
        } else if (resourceSchema.isSchemaAvailable(SCIMConstants.GROUP_CORE_SCHEMA_URI)) {
            String location = createLocationHeader(AbstractResourceManager.getResourceEndpointURL(
                    SCIMConstants.GROUP_ENDPOINT), scimObject.getId());
            scimObject.setLocation(location);
            scimObject.setResourceType(SCIMConstants.GROUP);
        } else if (resourceSchema.isSchemaAvailable(SCIMConstants.ROLE_SCHEMA_URI)) {
            scimObject.setResourceType(SCIMConstants.ROLE);
        }
        //check for required attributes
        validateSCIMObjectForRequiredAttributes(scimObject, resourceSchema);
        validateSchemaList(scimObject, resourceSchema);
    }

    /*
     * create location header from location and resourceID
     *
     * @param location
     * @param resourceID
     * @return
     */
    private static String createLocationHeader(String location, String resourceID) {
        String locationString = location + "/" + resourceID;
        return locationString;
    }

    /*
     * validate Retrieved SCIM Object in List
     *
     * @param scimObject
     * @param resourceSchema
     * @param reuqestedAttributes
     * @param requestedExcludingAttributes
     * @throws BadRequestException
     * @throws CharonException
     */
    public static void validateRetrievedSCIMObjectInList(AbstractSCIMObject scimObject,
                                                         SCIMResourceTypeSchema resourceSchema, String
                                                                 reuqestedAttributes,
                                                         String requestedExcludingAttributes)
            throws BadRequestException, CharonException {
        validateSCIMObjectForRequiredAttributes(scimObject, resourceSchema);
        validateReturnedAttributes(scimObject, reuqestedAttributes, requestedExcludingAttributes);
    }

    /*
     * validate Retrieved SCIM Object
     *
     * @param scimObject
     * @param resourceSchema
     * @param reuqestedAttributes
     * @param requestedExcludingAttributes
     * @throws BadRequestException
     * @throws CharonException
     */
    public static void validateRetrievedSCIMObject(AbstractSCIMObject scimObject,
                                                   SCIMResourceTypeSchema resourceSchema, String reuqestedAttributes,
                                                   String requestedExcludingAttributes)
            throws BadRequestException, CharonException {
        validateSCIMObjectForRequiredAttributes(scimObject, resourceSchema);
        validateReturnedAttributes(scimObject, reuqestedAttributes, requestedExcludingAttributes);
        validateSchemaList(scimObject, resourceSchema);
    }

    /**
     * Validate Retrieved SCIM Role Object.
     *
     * @param scimObject                   Role object.
     * @param requestedExcludingAttributes RequestedExcludingAttributes.
     */
    public static void validateRetrievedSCIMRoleObject(Role scimObject, String requestedAttributes,
            String requestedExcludingAttributes) {

        List<String> requestedExcludingAttributesList = null;
        List<String> requestedAttributesList = null;
        if (requestedExcludingAttributes != null) {
            // Make a list from the comma separated requestedExcludingAttributes.
            requestedExcludingAttributesList = Arrays.asList(requestedExcludingAttributes.split(","));
        }
        if (requestedAttributes != null) {
            // Make a list from the comma separated requestedAttributes.
            requestedAttributesList = Arrays.asList(requestedAttributes.split(","));
        }
        if (requestedAttributesList != null && requestedAttributesList.
                stream().noneMatch(SCIMConstants.RoleSchemaConstants.PERMISSIONS::equalsIgnoreCase)) {
            scimObject.setPermissions(new ArrayList<>());
        } else if (requestedExcludingAttributesList != null && requestedExcludingAttributesList.
                stream().anyMatch(SCIMConstants.RoleSchemaConstants.PERMISSIONS::equalsIgnoreCase)) {
            scimObject.setPermissions(new ArrayList<>());
        }
    }


    /*
     * Perform validation on SCIM Object update on service provider side
     *
     * @param oldObject
     * @param newObject
     * @param resourceSchema
     * @return
     * @throws CharonException
     */
    public static AbstractSCIMObject validateUpdatedSCIMObject(AbstractSCIMObject oldObject,
                                                               AbstractSCIMObject newObject,
                                                               SCIMResourceTypeSchema resourceSchema)
            throws CharonException, BadRequestException {

        AbstractSCIMObject validatedObject = null;
        if (newObject instanceof User) {
            //set display names for complex multivalued attributes
            setDisplayNameInComplexMultiValuedAttributes(newObject, resourceSchema);
        }
        //check for read only and immutable attributes
        validatedObject = checkIfReadOnlyAndImmutableAttributesModified(oldObject, newObject, resourceSchema);
        //copy meta attribute from old to new
        validatedObject.setAttribute(oldObject.getAttribute(SCIMConstants.CommonSchemaConstants.META));
        //copy id attribute to new group object
        validatedObject.setAttribute(oldObject.getAttribute(SCIMConstants.CommonSchemaConstants.ID));
        //edit last modified date
        validatedObject.setLastModifiedInstant(Instant.now());
        //check for required attributes.
        validateSCIMObjectForRequiredAttributes(newObject, resourceSchema);
        //check for schema list
        validateSchemaList(validatedObject, resourceSchema);

        return validatedObject;
    }

    /*
     * This method is to add meta data to the resource type resource
     *
     * @param scimObject
     * @return
     * @throws NotFoundException
     * @throws BadRequestException
     * @throws CharonException
     */
    public static AbstractSCIMObject validateResourceTypeSCIMObject(AbstractSCIMObject scimObject)
            throws NotFoundException, BadRequestException, CharonException {

        String endpoint = (String) (((SimpleAttribute) (scimObject.getAttribute
                (SCIMConstants.ResourceTypeSchemaConstants.NAME))).getValue());
        String location = createLocationHeader(AbstractResourceManager.getResourceEndpointURL(
                SCIMConstants.RESOURCE_TYPE_ENDPOINT), endpoint);

        scimObject.setLocation(location);
        scimObject.setResourceType(SCIMConstants.RESOURCE_TYPE);
        return scimObject;
    }
}


