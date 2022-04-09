package com.hangman.hangman.ui.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.*
import com.hangman.hangman.modal.Alphabets
import com.hangman.hangman.repository.*
import com.hangman.hangman.repository.database.entity.HistoryEntity
import com.hangman.hangman.repository.database.entity.WordsEntity
import com.hangman.hangman.utils.GameDifficulty
import com.hangman.hangman.utils.GameDifficultyPref
import com.hangman.hangman.utils.getDateAndTime
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

class GameViewModel(
    application: Application,
    private val repository: GameRepository
) : AndroidViewModel(application) {

    /**
     * Since creating a [mutableStateOf] object with a mutable collection type wasn't possible,
     * neither those values are reflecting as expected in UI, had to create extra data holder
     * property [UpdateWordGuessState] which holds current guesses made by player.
     * This state will be exposed to it's UI [GameScreen] to show player guesses.
     */
    var updateGuessesByPlayer by mutableStateOf(UpdateWordGuessState(arrayListOf()))
        private set

    // This acts like a mediator, makes sure indices are reset/updated.
    // Helps us determine whether game should be finished by reading values in indices.
    private val updatePlayerGuesses = arrayListOf<Char>()

    // Determine whether player won or lost the game
    private var playerWonTheCurrentLevel by mutableStateOf(false)

    // To prevent player keep playing the game
    var gameOver by mutableStateOf(false)

    // Generated a random country word to guess from database.
    private var wordToGuess by mutableStateOf("")

    // List of A-Z alphabets, let's player access alphabets in any order
    val alphabets by mutableStateOf(alphabetsList)

    // Keep track of attempts to find out whether or not to finish the game
    var attemptsLeftToGuess by mutableStateOf(6)

    // Number of points depend on length of the string for guessed word.
    var pointsScoredPerWord by mutableStateOf(0)

    // Starting level with 0, last level is 5
    var currentPlayerLevel by mutableStateOf(0)

    // Get shared preferences for value game difficulty.
    private val gameDifficultyPreferences = GameDifficultyPref(application)

    // Set default state game difficulty value to easy and update with latest changes.
    var gameDifficulty: GameDifficulty by mutableStateOf(GameDifficulty.EASY)

    // Contains 5 words in a list for current game, 1 for each level.
    private var guessingWordsForCurrentGame by mutableStateOf(listOf<WordsEntity>())

    init {
        viewModelScope.launch {
            // Get player saved game difficulty level from preferences.
            gameDifficulty = gameDifficultyPreferences.getGameDifficultyPref()
            // Based on difficulty level, get 5 unique random words from database.
            guessingWordsForCurrentGame = repository.getRandomGuessingWord(gameDifficulty)
            // From list of 5 words, starting from it's first index position sequentially return
            // a new word to guess for matching level.
            // If level is one first word will be returned, till level 5.
            wordToGuess = guessingWordsForCurrentGame[currentPlayerLevel].wordName
            // Reset guesses, update new word indices for current/next level word.
            updateOrResetWordToGuess()

            Timber.e(wordToGuess)
        }
    }

    // Called everytime when player chosen any word from list of alphabets.
    fun checkIfLetterMatches(
        alphabet: Alphabets
    ) {
        viewModelScope.launch {
            // Make sure to compare valid strings/chars by keeping it same letter case.
            val currentAlphabet = alphabet.alphabet.lowercase().first()
            val currentGuessingWord = wordToGuess.lowercase()

            if (currentGuessingWord.contains(currentAlphabet)) {
                // Since letter was a match, loop into indices range.
                for (notI in currentGuessingWord.indices) {
                    // From the matched word, find at which position alphabet match took place.
                    if (currentGuessingWord[notI] == currentAlphabet) {
                        // For matched position, pass that alphabet to the position to reflect in UI.
                        updatePlayerGuesses[notI] = currentAlphabet
                    }
                }

                // Reaching at this point, word has been guessed correctly.
                if (!updatePlayerGuesses.contains(' ')) {
                    // When none of the characters from word to guess contains empty character,
                    // player has won the current level, but not the whole game.
                    playerWonTheCurrentLevel = true
                    gameOver = false
                }
            } else {
                // When match wasn't successful, this will be executed.
                minimizeAttempt()
            }

            // if true, player won the level, now move to next level
            if (playerWonTheCurrentLevel) {
                // Reward points for level by length of guessed word.
                pointsScoredPerWord = wordToGuess.length
                // If level is reset/new, reset the attempts left to default.
                attemptsLeftToGuess = 6
                // Everytime player clears the level, update to new level by +1.
                currentPlayerLevel += 1
                // Get new word from saved guessing list by updated level.
                wordToGuess = guessingWordsForCurrentGame[currentPlayerLevel].wordName
                // Reset guesses, update new word indices for next level word.
                updateOrResetWordToGuess()
                // Game isn't over, but level is. For clarity update this game isn't over.
                gameOver = false
                // Saves the game to history, needs couple of changes yet.
                saveCurrentGameToHistory()
                Timber.e(wordToGuess)
            }
        }
    }

    // Everytime player chooses the alphabet, reduce the attempt by 1.
    // If attempt reaches 0, player has lost.
    private fun minimizeAttempt() {
        if (attemptsLeftToGuess > 0) {
            attemptsLeftToGuess -= 1
            gameOver = attemptsLeftToGuess == 0
        }
    }

    // Reset guesses, update new word indices for next level word.
    private fun updateOrResetWordToGuess() {
        updatePlayerGuesses.clear()
        for (i in wordToGuess.indices) {
            updatePlayerGuesses.add(' ')
            updateGuessesByPlayer = UpdateWordGuessState(
                updateGuess = updatePlayerGuesses
            )
        }
    }

    private suspend fun saveCurrentGameToHistory() {
        val (date, time) = getDateAndTime()

        repository.saveCurrentGameToHistory(
            HistoryEntity(
                gameId = UUID.randomUUID().toString(),
                gameScore = pointsScoredPerWord,
                gameLevel = currentPlayerLevel,
                gameSummary = gameOver,
                gameDifficulty = gameDifficulty,
                gamePlayedTime = time,
                gamePlayedDate = date
            )
        )
    }

    companion object {

        fun provideFactory(
            application: Application,
            repository: GameRepository,
        ): ViewModelProvider.AndroidViewModelFactory {
            return object : ViewModelProvider.AndroidViewModelFactory(application) {
                @Suppress("unchecked_cast")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                        return GameViewModel(application, repository) as T
                    }
                    throw IllegalArgumentException("Cannot create Instance for GameViewModel class")
                }
            }
        }
    }
}

data class UpdateWordGuessState(
    val updateGuess: ArrayList<Char>
)