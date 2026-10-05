package com.memphisreo.property;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Самодостатній сервіс модуля property: Address+Property створюються разом.
 * tenant_id/agent_id приходять від виклику (з JWT-принципала), не звідси.
 */
@Service
public class PropertyService {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final AddressRepository addressRepository;
    private final PropertyRepository propertyRepository;

    public PropertyService(AddressRepository addressRepository, PropertyRepository propertyRepository) {
        this.addressRepository = addressRepository;
        this.propertyRepository = propertyRepository;
    }

    public Property create(UUID tenantId, UUID agentId, CreatePropertyRequest request) {
        Address address = new Address();
        address.setCountryCode(request.countryCode());
        address.setRegion(request.region());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setStreet(request.street());
        address.setHouseNumber(request.houseNumber());
        address.setPostalCode(request.postalCode());
        address.setTenantId(tenantId);
        if (request.latitude() != null && request.longitude() != null) {
            Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(request.longitude(), request.latitude()));
            address.setGeoLocation(point);
        }
        address = addressRepository.save(address);

        Property property = new Property();
        property.setType(request.type());
        property.setAddressId(address.getId());
        property.setUnitNumber(request.unitNumber());
        property.setAreaSqm(request.areaSqm());
        property.setLandAreaSqm(request.landAreaSqm());
        property.setRooms(request.rooms());
        property.setBedrooms(request.bedrooms());
        property.setBathrooms(request.bathrooms());
        property.setFloor(request.floor());
        property.setTotalFloors(request.totalFloors());
        property.setYearBuilt(request.yearBuilt());
        property.setHasElevator(request.hasElevator());
        property.setParkingSpaces(request.parkingSpaces());
        property.setDescription(request.description());
        property.setAttributesJson(request.attributesJson());
        property.setStatus(Property.Status.ACTIVE);
        property.setCreatedByAgentId(agentId);
        property.setTenantId(tenantId);

        return propertyRepository.save(property);
    }
}
