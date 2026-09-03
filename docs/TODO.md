# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## The board loses to a rank median at running back and receiver

The board is held to what the league paid by `AuctionAccuracy`, and beside its error it now carries the
error of the most obvious thing anybody would try instead: `NAIVE` predicts each signing as the median this
league paid at the same position within six ranks, over its **other** seasons. No curve, no replacement
level, no value over replacement.

Pooled over 2022-2025, per position:

| POS | signings | board | rank median | |
| --- | --- | --- | --- | --- |
| QB | 56 | 9.09 | 12.36 | board by 3.27 |
| RB | 74 | 8.85 | 7.70 | **rank median by 1.15** |
| WR | 99 | 9.22 | 8.73 | **rank median by 0.49** |
| TE | 44 | 7.07 | 8.10 | board by 1.03 |
| PK | 39 | 0.90 | 0.90 | level |
| all | 312 | 7.77 | 8.07 | board by 0.31 |

**The board's whole advantage is quarterback.** Running back it loses in all four seasons, receiver in three
of four, and those two positions carry 173 of the 312 signings and about two thirds of the money. The
aggregate win is real but it is one position carrying four.

### Why this is the finding and not the levers

Every lever inside the price chain was measured and none returns much: the pot nothing, `MARKET_SHARE` 0.40
and `PRICE_STEEPNESS` 0.54 with hindsight nobody has, the priced depth negative, the franchise tag nothing.
That was written up as "no remaining lever of any size", which was true of the price transform and wrong as
a conclusion — the transform is handed a value column, and at running back and receiver it is the value
column that is losing. A gap of 1.15 at running back is larger than any of those five.

### What a fix would have to answer

- **Whether it is the curve or the market.** A rank median encodes what this league pays; value over
  replacement encodes what a rank scores. They differ most where a position's replacement level is least
  binding, which is exactly running back and receiver — 26 and 31 starters against quarterback's 20 under
  superflex. It may be that the model is wrong about backs, or that the league is, and `EDGE` exists on the
  assumption of the second.
- **Whether quarterback is the exception or the rule.** Superflex is what makes quarterback replacement
  bite, and it is the one position where the board is decisively better. If the board only adds value where
  replacement level is scarce, that is worth knowing plainly rather than having it averaged into one number.
- **Whether a blend beats either.** Nothing here has tried the obvious thing of pricing from both. That is a
  change to what the board *is*, not a constant, and it should not be made before the question above is
  answered.
- **How much of this survives another season.** 312 signings, four seasons, and the positional splits are 44
  to 99 apiece. The running back gap is consistent across all four seasons, which is the strongest thing
  here; the receiver gap is not.
