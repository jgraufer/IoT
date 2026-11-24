package com.iot.platform.edge.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapServiceTest {

    @Mock
    private EdgeIdentityRepository repository;

    @InjectMocks
    private BootstrapService service;

    @Test
    void emptyTable_generatesAndPersistsIdentity() {
        when(repository.findSingleton()).thenReturn(Optional.empty());

        service.initialize();

        ArgumentCaptor<EdgeIdentity> captor = ArgumentCaptor.forClass(EdgeIdentity.class);
        verify(repository).save(captor.capture());

        EdgeIdentity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1);
        assertThat(saved.getEdgeId()).isNotNull();
        assertThat(saved.getJwtSecret()).isNotBlank();
        assertThat(saved.getJwtSecret()).hasSize(44);
        assertThat(saved.getPairedAt()).isNull();
    }

    @Test
    void existingIdentity_isReusedWithoutSaving() {
        EdgeIdentity existing = EdgeIdentity.builder()
                .id(1)
                .edgeId(UUID.randomUUID())
                .jwtSecret("preexisting")
                .build();
        when(repository.findSingleton()).thenReturn(Optional.of(existing));

        service.initialize();

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
