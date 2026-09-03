# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## The board still loses to a rank median at running back and receiver

`AuctionStudy` scores every model with its target auction absent from the points curve, spend rate,
positional shares, steepness and rank history, and it carries `RANK_MEDIAN_RAW` beside them: each signing
predicted as the median this league paid at the same position within six ranks, over its **other** seasons.
No curve, no replacement level, no value over replacement.

Held out, pooled over 2022-2025:

| POS | signings | board | rank median | |
| --- | --- | --- | --- | --- |
| QB | 56 | 10.39 | 12.36 | board by 1.97 |
| RB | 74 | 8.86 | 7.70 | **rank median by 1.16** |
| WR | 100 | 9.68 | 8.66 | **rank median by 1.02** |
| TE | 44 | 6.98 | 8.10 | board by 1.12 |
| PK | 39 | 0.97 | 0.90 | rank median by 0.07 |
| all | 313 | 8.15 | 8.05 | **rank median by 0.10** |

**Moving the market shape from VOR to expected points narrowed this and did not close it.** Running back was
2.26 behind and is 1.16; receiver was 1.49 and is 1.02. The aggregate is still the wrong way round, and the
board's advantage is still one position carrying the rest.

Drop the first superflex auction, which every model finds hard and the rank median finds hardest, and it
gets worse rather than better: 7.99 against 7.65 over three folds, and 9.16 against 7.87 at running back.
See `POOLEDEX2022` in
[PROJECTION.md](fuad/PROJECTION.md#the-first-fold-flatters-every-model-so-it-is-reported-both-ways).

### What a fix would have to answer

- **Whether it is the curve or the market.** A rank median encodes what this league pays; the points curve
  encodes what a rank scores. Points shaping has now been tried and wins against VOR without beating the
  median, which narrows the question rather than answering it: the remaining gap is not replacement level.
- **Whether the blends were rejected for the right reason.** `BLEND_50` pools at 7.98 against points' 8.15
  and reaches 7.64 without 2022, where the rank median is 7.65. It was not shipped because it fails to beat
  points in every fold — and the only fold it fails in is 2022. That is a thinner reason than it looked.
- **Whether quarterback is the exception or the rule.** Superflex is what makes quarterback replacement
  bite, and it is the one position where the board is decisively better. If the board only adds value where
  replacement is scarce, that is worth knowing plainly rather than averaged into one number.

## The points-shaped price needs its first prospective season

What four folds cannot supply is a genuinely later season. When the 2026 post-auction roster exists, score
it before changing the model or adding 2026 to any fit. The question is whether points still beat VOR, and
whether any rank blend finally improves consistently rather than only in the pooled result.

## The receiver exponent is well determined and its consequences are not

`PRICE_STEEPNESS` at receiver is fitted at 4.00 on a profile-likelihood interval of [3.18, 4.88], so the
steep market is real and a flatter one is not available to be chosen. But that exponent applies across the
position's whole points range, and moving it across its own interval prices the top receiver anywhere from
about 106 to 153 — against a record whose largest receiver price is 94 and which has never had to price a
WR1 at all, the tag having removed every one of them.

Nothing here is wrong. What is missing is any observation that could narrow it: the six *bid away* events in
`tags.tsv` are the only times this league has revealed what it would pay for a tagged player, and
`PriceSteepness` does not see them. Whether they can be used — they are right-to-match auctions rather than
open bidding, and four of the six are pre-superflex — is the question. Kicker is the other end of the same
problem: its interval is [-0.81, 4.75], so the 1.60 it carries is a constant and not a measurement.
