package com.ewallet.service;

import com.ewallet.dto.request.CardRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.ElementAlreadyExistsException;
import com.ewallet.model.Card;
import com.ewallet.model.User;
import com.ewallet.repository.BankAccountRepository;
import com.ewallet.repository.CardRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;
    @Mock
    private BankAccountRepository bankAccountRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CurrentUserService currentUserService;

    private CardService cardService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        cardService = new CardService(cardRepository, bankAccountRepository, userRepository,
                currentUserService, clock);
    }

    @Test
    void create_validVisa_persistsMaskedCardWithBrandAndLast4() {
        CardRequest request = new CardRequest();
        request.setCardHolderName("Madhav");
        request.setCardNumber("4111111111111111"); // valid Luhn Visa test number
        request.setExpiryDate("12/30");
        request.setCvv("123");

        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(new User());
        when(cardRepository.existsByUserIdAndLast4AndExpiryMonthAndExpiryYear(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        CommandResponse response = cardService.create(request);

        ArgumentCaptor<Card> captor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(captor.capture());
        Card saved = captor.getValue();

        assertThat(saved.getLast4()).isEqualTo("1111");
        assertThat(saved.getBrand()).isEqualTo("Visa");
        assertThat(saved.getExpiryMonth()).isEqualTo(12);
        assertThat(saved.getExpiryYear()).isEqualTo(2030);
        assertThat(response).isNotNull();
    }

    @Test
    void create_invalidLuhn_throws() {
        CardRequest request = new CardRequest();
        request.setCardHolderName("Madhav");
        request.setCardNumber("4111111111111112"); // fails Luhn
        request.setExpiryDate("12/30");

        assertThatThrownBy(() -> cardService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void create_expiredCard_throws() {
        CardRequest request = new CardRequest();
        request.setCardHolderName("Madhav");
        request.setCardNumber("4111111111111111");
        request.setExpiryDate("01/20"); // in the past relative to fixed clock (2026)

        assertThatThrownBy(() -> cardService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void create_duplicateCard_throws() {
        CardRequest request = new CardRequest();
        request.setCardHolderName("Madhav");
        request.setCardNumber("5555555555554444"); // valid Luhn Mastercard test number
        request.setExpiryDate("06/29");

        when(currentUserService.getCurrentUserId()).thenReturn(1L);
        when(cardRepository.existsByUserIdAndLast4AndExpiryMonthAndExpiryYear(anyLong(), any(), anyInt(), anyInt()))
                .thenReturn(true);

        assertThatThrownBy(() -> cardService.create(request))
                .isInstanceOf(ElementAlreadyExistsException.class);
    }
}
