package io.libra.customer.persistence.mapper;

import io.libra.customer.domain.Customer;
import io.libra.customer.persistence.entity.CustomerEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    Customer toDomain(CustomerEntity entity);

    CustomerEntity toEntity(Customer domain);
}
