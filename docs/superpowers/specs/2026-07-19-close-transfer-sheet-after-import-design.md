# Close Transfer Sheet After Import Design

## Goal

Automatically close the configuration transfer sheet after either file import or pasted-text import has successfully written the imported configuration.

## Behavior

- A normal successful import closes the transfer sheet.
- An import whose data was written successfully but whose reminder refresh failed also closes the sheet. The warning does not turn the completed import back into a failure.
- Format, version, validation, file-read, and other import failures keep the sheet open.
- Failed pasted-text imports retain the current input text.
- Export completion never closes the transfer sheet automatically.

## Architecture

`TransferUiState` gains an explicit one-shot `importCompleted` flag. Both existing import channels continue to use the same `TransferViewModel` import operation. When that operation returns a `TransferImportResult`, the ViewModel sets `importCompleted` to `true`, including when `refreshFailed` produces a warning message.

`HomeScreen` observes `importCompleted` with a `LaunchedEffect`. When the flag becomes true while the sheet is open, it closes the sheet and invokes a new completion-consumed callback. `NianriNavHost` connects that callback to the ViewModel, which resets the flag and clears the now-hidden success or warning message. This prevents a completed event from closing a later sheet instance.

The UI does not infer completion from message text, selected tabs, or message severity. Import completion remains an explicit state transition, while export success and import errors retain their existing message behavior.

## Error Handling

- Exceptions leave `importCompleted` false.
- A successful import sets the flag only after the import service returns normally.
- Consuming completion clears both the flag and its success or warning message.
- Existing processing guards continue to prevent concurrent transfer operations.

## Testing

- ViewModel tests verify successful file and pasted-text imports set the completion flag.
- ViewModel tests verify reminder refresh warnings still set the completion flag.
- ViewModel tests verify failed imports leave the flag false and preserve pasted input.
- Compose tests verify success and warning close the sheet and consume completion.
- Compose tests verify errors leave the sheet visible.
- Existing transfer, launcher, clipboard, unit, lint, build, and connected-device suites remain green.
