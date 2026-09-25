# WearBoard

![Logo](assets/logo.jpg)

## T9 Keyboard for Wear OS

A compact, intuitive keyboard designed specifically for circular Wear OS displays. Built with modern Android development practices to provide fast text input while keeping the interface simple, responsive, and comfortable to use on a smartwatch.

## ✨ Features

- **Circular-Optimized UI:** Designed specifically for round Wear OS displays and small touch targets.
- **T9 Text Input:** Quickly enter words using multi-letter keys instead of a full QWERTY keyboard.
- **English & Ukrainian:** Supports both English and Ukrainian layouts with separate dictionaries.
- **Smart Word Suggestions:** Automatically suggests words while typing based on the current T9 sequence.
- **Personal Dictionary:** Words can be manually entered using multi-tap input and saved to the dictionary for future suggestions.
- **Multi-Tap Input:** Switch to traditional phone-style multi-tap input when you need to enter a word that is not recognized by the dictionary.
- **Shift & Caps Lock:** Supports one-time capitalization and Caps Lock for uppercase input.
- **Symbols & Numbers:** Long-press keys provide quick access to additional characters and numbers.
- **Lightweight Interface:** No unnecessary suggestion panels, large menus, or desktop-style keyboard elements.

## ⌨️ Controls & Interactions

The keyboard is designed around fast interactions that work naturally on a smartwatch:

| Interaction | Action |
| :--- | :--- |
| **Tap T9 Key** | Enter the next character and search for matching words |
| **Tap ABC** | Switch to manual multi-tap input |
| **Tap Save** | Save the current word to the personal dictionary |
| **Long Press Key** | Enter the key's secondary character or number |
| **Tap Shift** | Toggle uppercase for the next character |
| **Double Tap Shift** | Enable Caps Lock |
| **Tap Delete** | Delete the previous character |
| **Tap Space** | Insert a space and finish the current word |
| **Tap Enter** | Confirm the current input |
| **Swipe left** | Switch between English and Ukrainian layouts |
| **Swipe up** | Switch between language and symbol layouts |

## 🔤 T9 Input

T9 input allows words to be entered by repeatedly pressing the corresponding letter keys.

As you type, WearBoard searches the dictionary for words matching the current T9 sequence and automatically replaces the temporary input with the matching word when an exact-length dictionary entry is found.

Suggestions are ranked using dictionary frequency, allowing commonly used words to appear first.

## 📖 Personal Dictionary

When a word is not available in the dictionary, it can be entered manually using multi-tap input.

After entering the word, the **Save** action adds it to the dictionary. The word can then be recognized by T9 input in future sessions.

The dictionary also keeps a frequency value for words, allowing frequently selected words to receive higher priority in suggestions.

## 🌐 Languages

WearBoard currently supports:

- 🇬🇧 English
- 🇺🇦 Ukrainian

Each language uses its own dictionary database, allowing large dictionaries to be stored and searched efficiently on the watch.

The active keyboard language follows the selected input method language.

## 🛠 Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose for Wear OS
- **Input:** Android InputMethodService
- **Database:** Room / SQLite
- **Persistence:** Local dictionary databases
- **Asynchrony:** Kotlin Coroutines
- **Architecture:** State-based UI with asynchronous dictionary search

## 🚀 Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Build and install WearBoard on your Wear OS device or emulator.
4. Enable WearBoard as an available keyboard in the system keyboard settings.
5. Select WearBoard when entering text.

## 📚 Dictionaries

The English and Ukrainian dictionaries used by WearBoard are based on the dictionary data from [Traditional T9 (TT9)](https://github.com/sspanak/tt9).

TT9 combines word lists, frequency data, and other linguistic data from multiple open and publicly documented sources. Please refer to the TT9 repository and the accompanying dictionary license files for detailed source information and licensing requirements.

WearBoard does not claim ownership of the original dictionary data.

## 📝 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

*Built for fast text input on Wear OS.*
