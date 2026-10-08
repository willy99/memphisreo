package com.memphisreo.platform.crm;

import com.memphisreo.crm.Client;
import com.memphisreo.crm.ClientRequirement;
import com.memphisreo.platform.crm.MatchingService.PropertyMatch;
import com.memphisreo.platform.sale.SaleDtos.TimelineEntry;

import java.util.List;
import java.util.UUID;

public final class ClientDtos {

    private ClientDtos() {
    }

    /** Об'єкти, де контакт — власник. */
    public record OwnedProperty(UUID id, String title, String status) {
    }

    public record ClientDetails(Client client, ClientRequirement requirement, List<PropertyMatch> matches,
                                List<OwnedProperty> owned, List<TimelineEntry> timeline) {
    }

    /** Запис у журнал: дзвінок / повідомлення / зустріч / нотатка. */
    public record NoteRequest(String kind, String note) {
    }
}
