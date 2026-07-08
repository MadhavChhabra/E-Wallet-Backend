package com.ewallet.service;

import com.ewallet.dto.request.CardRequest;
import com.ewallet.dto.response.CardResponse;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.ElementAlreadyExistsException;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.model.BankAccount;
import com.ewallet.model.Card;
import com.ewallet.repository.BankAccountRepository;
import com.ewallet.repository.CardRepository;
import com.ewallet.repository.UserRepository;
import com.ewallet.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages saved payment cards. Card data is tokenized and masked on save — no
 * PAN or CVV is ever persisted.
 */
@Slf4j(topic = "CardService")
@Service
@RequiredArgsConstructor
public class CardService {

    private final CardRepository cardRepository;
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<CardResponse> findAllForCurrentUser() {
        return cardRepository.findByUserId(currentUserService.getCurrentUserId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public CommandResponse create(CardRequest request) {
        String number = request.getCardNumber().replaceAll("\\s", "");
        if (!passesLuhn(number)) {
            throw new IllegalArgumentException("Card number is invalid");
        }

        int[] expiry = parseExpiry(request.getExpiryDate());
        String last4 = number.substring(number.length() - 4);
        Long userId = currentUserService.getCurrentUserId();

        if (cardRepository.existsByUserIdAndLast4AndExpiryMonthAndExpiryYear(userId, last4, expiry[0], expiry[1])) {
            throw new ElementAlreadyExistsException("This card is already saved");
        }

        Card card = new Card();
        card.setCardHolderName(request.getCardHolderName().trim());
        card.setLast4(last4);
        card.setBrand(detectBrand(number));
        card.setExpiryMonth(expiry[0]);
        card.setExpiryYear(expiry[1]);
        card.setUser(userRepository.getReferenceById(userId));
        card.setCreatedAt(LocalDateTime.now(clock));

        if (request.getBankAccountId() != null) {
            BankAccount account = bankAccountRepository.findByIdWithUser(request.getBankAccountId())
                    .orElseThrow(() -> new NoSuchElementFoundException("Bank account not found"));
            if (!account.getUser().getId().equals(userId)) {
                throw new AccessDeniedException("You do not have access to this bank account");
            }
            card.setBankAccount(account);
        }

        cardRepository.save(card);
        log.info("Card saved (brand={} last4={} userId={})", card.getBrand(), last4, userId);
        return CommandResponse.builder().id(card.getId()).build();
    }

    @Transactional
    public void deleteById(long id) {
        Card card = cardRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementFoundException("Card not found"));
        if (!card.getUser().getId().equals(currentUserService.getCurrentUserId())) {
            throw new AccessDeniedException("You do not have access to this card");
        }
        cardRepository.delete(card);
        log.info("Card deleted (id={} userId={})", id, currentUserService.getCurrentUserId());
    }

    private CardResponse toDto(Card card) {
        String masked = "**** **** **** " + card.getLast4();
        String expiry = String.format("%02d/%02d", card.getExpiryMonth(), card.getExpiryYear() % 100);
        return CardResponse.builder()
                .id(card.getId())
                .cardHolderName(card.getCardHolderName())
                .maskedNumber(masked)
                .last4(card.getLast4())
                .brand(card.getBrand())
                .expiryDate(expiry)
                .bankAccountId(card.getBankAccount() != null ? card.getBankAccount().getId() : null)
                .token(card.getToken())
                .build();
    }

    private int[] parseExpiry(String expiryDate) {
        String[] parts = expiryDate.split("/");
        int month = Integer.parseInt(parts[0]);
        int year = Integer.parseInt(parts[1]);
        if (year < 100) {
            year += 2000;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean inPast = year < now.getYear() || (year == now.getYear() && month < now.getMonthValue());
        if (inPast) {
            throw new IllegalArgumentException("Card has expired");
        }
        return new int[]{month, year};
    }

    private String detectBrand(String number) {
        if (number.startsWith("4")) {
            return "Visa";
        }
        if (number.matches("^5[1-5].*") || number.matches("^2(2[2-9]|[3-6]|7[01]|720).*")) {
            return "Mastercard";
        }
        if (number.matches("^3[47].*")) {
            return "American Express";
        }
        if (number.startsWith("6")) {
            return "Discover";
        }
        return "Card";
    }

    /** Luhn checksum validation. */
    private boolean passesLuhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = number.charAt(i) - '0';
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
