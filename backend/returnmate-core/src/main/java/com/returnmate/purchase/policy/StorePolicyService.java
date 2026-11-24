package com.returnmate.purchase.policy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The store catalogue: every store's policy, loaded ONCE at startup and kept in memory.
 *
 * <p>Why cache? The policies are the same for every user and only change through a new
 * migration, which means a redeploy, which means a fresh load. So there's no reason to
 * query the database every time someone adds a purchase.
 */
@Service
public class StorePolicyService {

    private final Map<String, ReturnPolicy> byCode;      // in name order, for the dropdown
    private final Map<String, ReturnPolicy> byAlias;

    @Autowired
    public StorePolicyService(StorePolicyRepository repository) {
        this(repository.findAll(Sort.by("name")).stream().map(StorePolicy::toReturnPolicy).toList());
    }

    /** For tests: a catalogue built from hand-made policies. */
    public StorePolicyService(List<ReturnPolicy> policies) {
        Map<String, ReturnPolicy> codes = new LinkedHashMap<>();
        Map<String, ReturnPolicy> aliases = new HashMap<>();
        for (ReturnPolicy policy : policies) {
            codes.put(policy.code(), policy);
            aliases.put(normalize(policy.code()), policy);
            policy.aliases().forEach(alias -> aliases.put(normalize(alias), policy));
        }
        this.byCode = Collections.unmodifiableMap(codes);
        this.byAlias = Map.copyOf(aliases);
    }

    public List<ReturnPolicy> all() {
        return List.copyOf(byCode.values());
    }

    public Optional<ReturnPolicy> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(byCode.get(code.trim().toLowerCase(Locale.ROOT)));
    }

    /** For a store code the client chose: unknown is the client's mistake (400). */
    public ReturnPolicy require(String code) {
        return find(code).orElseThrow(() -> new IllegalArgumentException("Unknown store: " + code));
    }

    /**
     * Which known store a merchant name refers to: "Amazon.com", "BEST BUY", "Macy’s".
     * Exact match after normalising, never a fuzzy guess: wrongly deciding a local shop
     * is Costco would give the user a wrong deadline. No match = the user picks the store
     * or enters the deadline themselves.
     */
    public Optional<ReturnPolicy> matchMerchant(String merchantName) {
        if (merchantName == null) {
            return Optional.empty();
        }
        String key = normalize(merchantName);
        return key.isEmpty() ? Optional.empty() : Optional.ofNullable(byAlias.get(key));
    }

    /** "Macy’s" -> "macys", "Amazon.com" -> "amazoncom", "ＡＭＡＺＯＮ" -> "amazon". */
    static String normalize(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
