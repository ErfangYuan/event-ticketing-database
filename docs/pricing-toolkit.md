# Pricing toolkit

Choose a valid venue, genre and current/future UTC date. The toolkit uses completed dates in the preceding 365 days, excluding cancelled performances, at venues with 70–130% of the target's physical capacity. It takes at most 50 comparable performances, preferring the same genre and country/city, then the same genre elsewhere, then the same segment. Each performance occurs once; more recent performances and stable IDs break ties.

With fewer than three comparables, the explicit fallback is three tiers: 30% at 180, 40% at 120 and 30% at 75. This is a starting example, not a market valuation. Otherwise, the most common tier count is clamped to two through four (smaller count wins a tie). Tiers are ranked by descending price. The toolkit averages prices, physical-capacity shares and active sales divided by physical tier capacity across comparable tiers, then normalizes the shares. Display percentages are rounded to one decimal place; historical blocked seats remain part of this physical-capacity basis. Confidence reflects price variation across samples, not predictive certainty.

The suggested shares describe a planning target. When creating a performance, the organizer explicitly assigns every entire venue section to one tier, preserving the venue's actual seats and standing capacity. The assignment can therefore differ from the suggested percentages. Accept the suggested prices or enter one or more manual tiers. The demonstration dataset separately retains at least two tiers per performance.

The optional estimate uses illustrative elasticity E = -1. With price p, target tier capacity C, historical sell-through s and relative price change d:

`old = C × p × s`

`new = C × p × (1 + d) × clamp(s × (1 − d), 0, 1)`

The displayed change is `new − old`. An unavailable sell-through gives a zero-demand example. Values must be finite; price and capacity nonnegative; s within [0,1]; d at least -1. Overflow is rejected. This arithmetic makes no causal forecast or guarantee of sales. The TUI estimate always refers to the displayed toolkit suggestion, including when the organizer later enters manual prices.
