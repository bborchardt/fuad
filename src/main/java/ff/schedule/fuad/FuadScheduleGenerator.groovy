package ff.schedule.fuad

import ff.data.fuad.FuadMatchup
import ff.data.mfl.MflFranchise

/**
 * Generates a random 14 week head-to-head schedule from a league's franchises.
 *
 * Franchises are split into their two divisions (of equal size d). Division
 * rivals meet exactly twice; every cross-division opponent is guaranteed at
 * least one meeting. This is built from a standard round-robin "circle
 * method": division rounds are rotated for 2*(d-1) weeks (even d) or, when d
 * is odd, 2*d weeks with a "bye" team each round that instead plays a
 * reserved cross-division rival that week. Any weeks left over are filled by
 * cross-division-only rounds, rotated through the remaining cross opponents
 * so every one of them is scheduled. This only balances out to exactly 14
 * weeks with no byes when both divisions are the same size d, with
 * 2 <= d <= 5, which is why other shapes are rejected below.
 *
 * The number of weeks and the source of randomness are both overridable via
 * the constructor, primarily so tests can shrink the schedule and fix the
 * seed to get a deterministic, fully checkable result.
 */
class FuadScheduleGenerator {

    private final int numWeeks
    private final Random random

    FuadScheduleGenerator(int numWeeks = 14, Random random = new Random()) {
        this.numWeeks = numWeeks
        this.random = random
    }

    List<FuadMatchup> generate(Collection<MflFranchise> franchises) {
        Map<String, List<MflFranchise>> byDivision = franchises.groupBy { it.division }
        if (byDivision.size() != 2) {
            throw new IllegalArgumentException("Schedule generation requires exactly 2 divisions, found ${byDivision.keySet()}.")
        }
        List<List<MflFranchise>> divisionLists = byDivision.values().toList()
        List<MflFranchise> divisionA = new ArrayList<>(divisionLists[0])
        List<MflFranchise> divisionB = new ArrayList<>(divisionLists[1])
        int d = divisionA.size()
        if (divisionB.size() != d) {
            throw new IllegalArgumentException(
                    "Schedule generation requires equal-size divisions, found sizes $d and ${divisionB.size()}.")
        }

        Collections.shuffle(divisionA, random)
        Collections.shuffle(divisionB, random)

        boolean oddDivision = d % 2 == 1
        int numPhase1Rounds = oddDivision ? 2 * d : 2 * (d - 1)
        int crossOpponentsToCover = oddDivision ? d - 1 : d
        int remainingRounds = numWeeks - numPhase1Rounds
        if (remainingRounds < crossOpponentsToCover) {
            throw new IllegalArgumentException(
                    "Cannot build a $numWeeks week, bye-free schedule for divisions of size $d.")
        }

        List<List<List<MflFranchise>>> rounds = []
        rounds.addAll(phase1Rounds(divisionA, divisionB, oddDivision, numPhase1Rounds))
        rounds.addAll(phase2Rounds(divisionA, divisionB, oddDivision, remainingRounds))

        Collections.shuffle(rounds, random)

        List<FuadMatchup> matchups = []
        rounds.eachWithIndex { weekPairs, idx ->
            int week = idx + 1
            weekPairs.each { pair ->
                matchups << new FuadMatchup(week, pair[0].id, pair[1].id)
            }
        }
        matchups
    }

    private List<List<List<MflFranchise>>> phase1Rounds(
            List<MflFranchise> divisionA, List<MflFranchise> divisionB, boolean oddDivision, int numRounds) {
        List<MflFranchise> rotationA = rotationSeed(divisionA, oddDivision)
        List<MflFranchise> rotationB = rotationSeed(divisionB, oddDivision)
        List<List<List<MflFranchise>>> rounds = []
        numRounds.times {
            List<List<MflFranchise>> weekPairs = []
            weekPairs.addAll(divisionPairs(rotationA))
            weekPairs.addAll(divisionPairs(rotationB))
            if (oddDivision) {
                weekPairs << [byeFranchise(rotationA), byeFranchise(rotationB)]
            }
            rounds << weekPairs
            rotationA = rotate(rotationA)
            rotationB = rotate(rotationB)
        }
        rounds
    }

    private List<List<List<MflFranchise>>> phase2Rounds(
            List<MflFranchise> divisionA, List<MflFranchise> divisionB, boolean oddDivision, int numRounds) {
        int d = divisionA.size()
        List<Integer> offsets = oddDivision ? (1..<d).toList() : (0..<d).toList()
        Collections.shuffle(offsets, random)
        List<List<List<MflFranchise>>> rounds = []
        numRounds.times { i ->
            int offset = offsets[i % offsets.size()]
            List<List<MflFranchise>> weekPairs = (0..<d).collect { j ->
                [divisionA[j], divisionB[(j + offset) % d]]
            }
            rounds << weekPairs
        }
        rounds
    }

    private List<MflFranchise> rotationSeed(List<MflFranchise> division, boolean oddDivision) {
        List<MflFranchise> seed = new ArrayList<>(division)
        if (oddDivision) {
            seed << null
        }
        seed
    }

    private List<List<MflFranchise>> divisionPairs(List<MflFranchise> rotation) {
        int m = rotation.size()
        List<List<MflFranchise>> pairs = []
        (0..<(m / 2)).each { i ->
            MflFranchise a = rotation[i]
            MflFranchise b = rotation[m - 1 - i]
            if (a != null && b != null) {
                pairs << [a, b]
            }
        }
        pairs
    }

    private MflFranchise byeFranchise(List<MflFranchise> rotation) {
        int m = rotation.size()
        for (int i = 0; i < m / 2; i++) {
            MflFranchise a = rotation[i]
            MflFranchise b = rotation[m - 1 - i]
            if (a == null) {
                return b
            }
            if (b == null) {
                return a
            }
        }
        throw new IllegalStateException('No bye team found in rotation.')
    }

    private List<MflFranchise> rotate(List<MflFranchise> rotation) {
        int m = rotation.size()
        if (m <= 2) {
            return new ArrayList<>(rotation)
        }
        List<MflFranchise> rotated = new ArrayList<>(rotation)
        MflFranchise last = rotated.remove(m - 1)
        rotated.add(1, last)
        rotated
    }
}
