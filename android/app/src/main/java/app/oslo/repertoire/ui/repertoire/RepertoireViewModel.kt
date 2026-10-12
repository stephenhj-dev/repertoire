package app.oslo.repertoire.ui.repertoire

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.oslo.repertoire.data.FollowedPersonRow
import app.oslo.repertoire.data.UiPrefs
import app.oslo.repertoire.repertoire.BodyOfWork
import app.oslo.repertoire.repertoire.PersonWork
import app.oslo.repertoire.repertoire.Repertoire
import app.oslo.repertoire.repertoire.WorkProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One person on the Repertoire tab. [work] is null while their body of work loads. */
@Immutable
data class PersonCard(
    val row: FollowedPersonRow,
    val work: PersonWork? = null,
    val progress: WorkProgress? = null,
    val failed: Boolean = false,
    /** Just completed: play the milestone animation once. */
    val celebrate: Boolean = false,
)

class RepertoireViewModel(app: Application) : AndroidViewModel(app) {
    private val repertoire = Repertoire(app)
    private val prefs = UiPrefs(app)
    private val gate = Semaphore(3)

    private var rows: List<FollowedPersonRow> = emptyList()
    private var works: Map<Int, PersonWork> = emptyMap()
    private var failed: Set<Int> = emptySet()
    private var watched: Set<String> = emptySet()
    private var watchedReady = false
    private val loading = HashSet<Int>()
    private var celebrating: Set<Int> = emptySet()

    var cards by mutableStateOf<List<PersonCard>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            repertoire.people.collect {
                rows = it
                loaded = true
                it.forEach { row -> load(row.personId) }
                publish()
            }
        }
        viewModelScope.launch {
            repertoire.watched.collect {
                watched = it
                watchedReady = true
                publish()
            }
        }
    }

    /** Overall "x of y" across everyone (titles counted once). */
    val totals: Pair<Int, Int>
        get() {
            val all = cards.mapNotNull { it.work }.flatMap { it.items }.distinctBy { it.key }
            return all.count { it.key in watched } to all.size
        }

    fun retry(id: Int) {
        failed = failed - id
        load(id)
        publish()
    }

    fun celebrated(id: Int) {
        celebrating = celebrating - id
        publish()
    }

    private fun load(id: Int) {
        if (id in works || !loading.add(id)) return
        viewModelScope.launch {
            try {
                val work = gate.withPermit { repertoire.work(id) }
                works = works + (id to work)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed = failed + id
            } finally {
                loading.remove(id)
            }
            publish()
        }
    }

    private fun publish() {
        val seen = prefs.celebrated.toMutableSet()
        val out = rows.map { row ->
            val work = works[row.personId]
            val progress = work?.let { BodyOfWork.progress(it.items, watched) }
            val key = row.personId.toString()
            if (progress != null && watchedReady) {
                if (progress.completed && key !in seen) {
                    // First time complete (or complete again after un-watching): celebrate once.
                    seen += key
                    celebrating = celebrating + row.personId
                } else if (!progress.completed) {
                    seen -= key
                }
            }
            PersonCard(row, work, progress, failed = row.personId in failed && work == null, celebrate = row.personId in celebrating)
        }
        prefs.celebrated = seen
        cards = out
    }
}
