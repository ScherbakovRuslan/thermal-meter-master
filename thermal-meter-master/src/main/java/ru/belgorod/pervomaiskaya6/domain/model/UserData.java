package ru.belgorod.pervomaiskaya6.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserData {

    public UserData(long chatId, String phoneNumber, String firstName, String lastName) {
        this.id = chatId;
        this.phoneNumber = phoneNumber;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Id
    private Long id;
    private Integer roomNumber;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private Double thermalMeterValue;
    private Double oldThermalMeterValue;
}
