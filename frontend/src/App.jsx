import { useState, useEffect } from 'react';
import axios from 'axios';
import './App.css';

// Константи API
const API_BASE = 'http://localhost:8080';
const API_PLANS = `${API_BASE}/api/travel-plans`;
const API_LOCATIONS = `${API_BASE}/api/locations`;

function App() {
    // --- Стан додатка ---
    const [plans, setPlans] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(false);

    // Стан для Планів
    const [isPlanModalOpen, setIsPlanModalOpen] = useState(false);
    const [editingPlanId, setEditingPlanId] = useState(null);
    const [editingPlanVersion, setEditingPlanVersion] = useState(null);
    const [selectedPlanDetails, setSelectedPlanDetails] = useState(null);

    // Стан для Локацій
    const [editingLocationId, setEditingLocationId] = useState(null);
    const [editingLocationVersion, setEditingLocationVersion] = useState(null);

    // Форми
    const [planForm, setPlanForm] = useState({
        title: '', description: '', budget: 0, currency: 'USD', isPublic: false, startDate: '', endDate: ''
    });

    const [locationForm, setLocationForm] = useState({
        name: '', address: '', notes: '', budget: 0,
        latitude: 0, longitude: 0, visitOrder: 1,
        arrivalDate: '', departureDate: ''
    });

    // --- Завантаження даних ---

    const fetchPlans = async (pageNumber = 0) => {
        setLoading(true);
        try {
            const res = await axios.get(`${API_PLANS}?page=${pageNumber}&size=5&sort=createdAt,desc`);
            setPlans(res.data.content);
            setTotalPages(res.data.totalPages);
            setPage(res.data.number);
        } catch (err) {
            console.error("Error fetching plans:", err);
        } finally {
            setLoading(false);
        }
    };

    const fetchPlanDetails = async (id) => {
        try {
            const res = await axios.get(`${API_PLANS}/${id}`);
            setSelectedPlanDetails(res.data);
            resetLocationForm();
        } catch (err) {
            alert("Error loading plan details: " + (err.response?.data?.detail || err.message));
        }
    };

    useEffect(() => { fetchPlans(); }, []);

    // --- Логіка Планів (Travel Plans) ---

    const openPlanModal = (plan = null) => {
        if (plan) {
            setEditingPlanId(plan.id);
            setEditingPlanVersion(plan.version);
            setPlanForm({
                title: plan.title,
                description: plan.description || '',
                budget: plan.budget || 0,
                currency: plan.currency || 'USD',
                isPublic: plan.isPublic,
                startDate: plan.startDate || '',
                endDate: plan.endDate || ''
            });
        } else {
            setEditingPlanId(null);
            setEditingPlanVersion(null);
            resetPlanForm();
        }
        setIsPlanModalOpen(true);
    };

    const resetPlanForm = () => {
        setPlanForm({ title: '', description: '', budget: 0, currency: 'USD', isPublic: false, startDate: '', endDate: '' });
    };

    const handlePlanSubmit = async (e) => {
        e.preventDefault();
        const payload = { ...planForm };
        if (!payload.startDate) payload.startDate = null;
        if (!payload.endDate) payload.endDate = null;

        try {
            if (editingPlanId) {
                await axios.put(`${API_PLANS}/${editingPlanId}`, { ...payload, version: editingPlanVersion });
                if (selectedPlanDetails?.id === editingPlanId) fetchPlanDetails(editingPlanId);
            } else {
                await axios.post(API_PLANS, payload);
            }
            setIsPlanModalOpen(false);
            fetchPlans(page);
        } catch (err) {
            console.error(err);
            alert(`Error: ${err.response?.data?.detail || err.message}`);
        }
    };

    const handleDeletePlan = async (id) => {
        if (!window.confirm("Are you sure you want to delete this plan?")) return;
        try {
            await axios.delete(`${API_PLANS}/${id}`);
            if (selectedPlanDetails?.id === id) setSelectedPlanDetails(null);
            fetchPlans(page);
        } catch (err) {
            alert("Error deleting plan");
        }
    };

    // --- Логіка Локацій (Locations) ---

    const editLocation = (loc) => {
        setEditingLocationId(loc.id);
        setEditingLocationVersion(loc.version);
        setLocationForm({
            name: loc.name,
            address: loc.address || '',
            notes: loc.notes || '',
            budget: loc.budget || 0,
            latitude: loc.latitude || 0,
            longitude: loc.longitude || 0,
            visitOrder: loc.visitOrder || 1,
            arrivalDate: loc.arrivalDate ? loc.arrivalDate.substring(0, 16) : '',
            departureDate: loc.departureDate ? loc.departureDate.substring(0, 16) : ''
        });
    };

    const resetLocationForm = () => {
        setEditingLocationId(null);
        setEditingLocationVersion(null);
        setLocationForm({
            name: '', address: '', notes: '', budget: 0,
            latitude: 0, longitude: 0, visitOrder: 1,
            arrivalDate: '', departureDate: ''
        });
    };

    const handleLocationSubmit = async (e) => {
        e.preventDefault();
        if (!selectedPlanDetails) return;

        const payload = { ...locationForm };
        if (payload.arrivalDate) payload.arrivalDate = new Date(payload.arrivalDate).toISOString();
        else payload.arrivalDate = null;

        if (payload.departureDate) payload.departureDate = new Date(payload.departureDate).toISOString();
        else payload.departureDate = null;

        try {
            if (editingLocationId) {
                await axios.put(`${API_LOCATIONS}/${editingLocationId}`, { ...payload, version: editingLocationVersion });
            } else {
                await axios.post(`${API_PLANS}/${selectedPlanDetails.id}/locations`, payload);
            }
            resetLocationForm();
            fetchPlanDetails(selectedPlanDetails.id);
        } catch (err) {
            console.error(err);
            alert(`Error saving location: ${err.response?.data?.detail || err.message}`);
        }
    };

    const handleDeleteLocation = async (locId) => {
        if (!window.confirm("Remove this location?")) return;
        try {
            await axios.delete(`${API_LOCATIONS}/${locId}`);
            fetchPlanDetails(selectedPlanDetails.id);
        } catch (err) {
            alert("Error deleting location");
        }
    };

    // --- Render ---

    return (
        <div className="container">
            <header className="app-header">
                <h1>🌍 Traveler Sharding App</h1>
                <button className="btn-primary" onClick={() => openPlanModal()}>+ Create New Plan</button>
            </header>

            <div className="main-layout">
                {/* ЛІВА КОЛОНКА */}
                <div className="plans-list-section">
                    <h2>Your Plans</h2>
                    {loading && <p>Loading plans...</p>}

                    <div className="plans-grid">
                        {plans.map(plan => (
                            <div
                                key={plan.id}
                                className={`plan-card ${selectedPlanDetails?.id === plan.id ? 'active' : ''}`}
                                onClick={() => fetchPlanDetails(plan.id)}
                            >
                                <div className="card-header">
                                    <h3>{plan.title}</h3>
                                </div>
                                <div className="card-dates">
                                    📅 {plan.startDate} — {plan.endDate}
                                </div>
                            </div>
                        ))}
                        {plans.length === 0 && !loading && <p className="text-muted">No plans found. Create one!</p>}
                    </div>

                    <div className="pagination">
                        <button disabled={page === 0} onClick={() => fetchPlans(page - 1)}>← Prev</button>
                        <span> Page {page + 1} of {totalPages || 1} </span>
                        <button disabled={page >= totalPages - 1} onClick={() => fetchPlans(page + 1)}>Next →</button>
                    </div>
                </div>

                {/* ПРАВА КОЛОНКА */}
                <div className="plan-details-section">
                    {selectedPlanDetails ? (
                        <>
                            <div className="details-header">
                                <div>
                                    <h2>{selectedPlanDetails.title}</h2>
                                    <span className={`badge ${selectedPlanDetails.isPublic ? 'public' : 'private'}`}>
                        {selectedPlanDetails.isPublic ? 'Public' : 'Private'}
                    </span>
                                </div>
                                <div className="details-actions" style={{display:'flex', gap:'10px'}}>
                                    <button onClick={() => openPlanModal(selectedPlanDetails)}>✏️ Edit</button>
                                    <button className="btn-danger" onClick={() => handleDeletePlan(selectedPlanDetails.id)}>🗑️ Del</button>
                                    <button onClick={() => setSelectedPlanDetails(null)}>✕</button>
                                </div>
                            </div>

                            <p className="description">{selectedPlanDetails.description || <em>No description provided.</em>}</p>

                            <div className="stats-row">
                                <div>💰 <strong>Budget:</strong> {selectedPlanDetails.budget} {selectedPlanDetails.currency}</div>
                                <div>📅 <strong>Dates:</strong> {selectedPlanDetails.startDate} - {selectedPlanDetails.endDate}</div>
                            </div>

                            <hr style={{borderColor:'var(--border-color)', margin:'20px 0'}} />

                            <h3>📍 Itinerary ({selectedPlanDetails.locations ? selectedPlanDetails.locations.length : 0})</h3>

                            <div className="locations-list">
                                {selectedPlanDetails.locations && selectedPlanDetails.locations.map(loc => (
                                    <div key={loc.id} className="location-item">
                                        <div className="loc-info">
                                            <strong>#{loc.visitOrder} {loc.name}</strong>
                                            <span className="loc-address">{loc.address}</span>
                                            {loc.budget > 0 && <span className="loc-budget">💰 {loc.budget}</span>}
                                            {loc.notes && <p className="loc-notes">📝 {loc.notes}</p>}
                                        </div>
                                        <div className="loc-actions">
                                            <button onClick={() => editLocation(loc)}>✏️</button>
                                            <button className="btn-danger" style={{marginLeft:'5px'}} onClick={() => handleDeleteLocation(loc.id)}>✕</button>
                                        </div>
                                    </div>
                                ))}
                            </div>

                            {/* Форма додавання/редагування локації */}
                            <div className="location-form">
                                <h4>{editingLocationId ? 'Edit Location' : 'Add New Location'}</h4>
                                <form onSubmit={handleLocationSubmit}>

                                    <div className="form-group">
                                        <label>Location Name</label>
                                        <input placeholder="e.g. Eiffel Tower" value={locationForm.name} onChange={e => setLocationForm({...locationForm, name: e.target.value})} required />
                                    </div>

                                    <div className="form-group">
                                        <label>Address</label>
                                        <input placeholder="e.g. Champ de Mars, 5 Av. Anatole France" value={locationForm.address} onChange={e => setLocationForm({...locationForm, address: e.target.value})} />
                                    </div>

                                    <div className="row">
                                        <div className="form-group">
                                            <label>Cost</label>
                                            <input type="number" placeholder="0.00" value={locationForm.budget} onChange={e => setLocationForm({...locationForm, budget: e.target.value})} />
                                        </div>
                                        <div className="form-group">
                                            <label>Visit Order</label>
                                            <input type="number" value={locationForm.visitOrder} onChange={e => setLocationForm({...locationForm, visitOrder: e.target.value})} />
                                        </div>
                                    </div>

                                    <div className="row">
                                        <div className="form-group">
                                            <label>Latitude</label>
                                            <input type="number" placeholder="48.858" value={locationForm.latitude} onChange={e => setLocationForm({...locationForm, latitude: e.target.value})} />
                                        </div>
                                        <div className="form-group">
                                            <label>Longitude</label>
                                            <input type="number" placeholder="2.294" value={locationForm.longitude} onChange={e => setLocationForm({...locationForm, longitude: e.target.value})} />
                                        </div>
                                    </div>

                                    <div className="row">
                                        <div className="form-group">
                                            <label>Arrival Time</label>
                                            <input type="datetime-local" value={locationForm.arrivalDate} onChange={e => setLocationForm({...locationForm, arrivalDate: e.target.value})} />
                                        </div>
                                        <div className="form-group">
                                            <label>Departure Time</label>
                                            <input type="datetime-local" value={locationForm.departureDate} onChange={e => setLocationForm({...locationForm, departureDate: e.target.value})} />
                                        </div>
                                    </div>

                                    <div className="form-group">
                                        <label>Notes</label>
                                        <textarea placeholder="Don't forget the tickets..." value={locationForm.notes} onChange={e => setLocationForm({...locationForm, notes: e.target.value})} rows="3" />
                                    </div>

                                    <div style={{marginTop:'10px'}}>
                                        <button type="submit" className="btn-primary">{editingLocationId ? 'Update Location' : 'Add Location'}</button>
                                        {editingLocationId && <button type="button" onClick={resetLocationForm} style={{marginLeft:'10px'}}>Cancel</button>}
                                    </div>
                                </form>
                            </div>

                        </>
                    ) : (
                        <div className="placeholder" style={{textAlign:'center', color:'var(--text-muted)', marginTop:'50px'}}>
                            <h3>👈 Select a plan</h3>
                            <p>Select a travel plan from the list to view details and manage locations.</p>
                        </div>
                    )}
                </div>
            </div>

            {/* MODAL: CREATE/EDIT PLAN */}
            {isPlanModalOpen && (
                <div className="modal-overlay">
                    <div className="modal">
                        <h2>{editingPlanId ? 'Edit Plan' : 'Create New Plan'}</h2>
                        <form onSubmit={handlePlanSubmit}>

                            <div className="form-group">
                                <label>Plan Title</label>
                                <input placeholder="e.g. Summer in Paris" value={planForm.title} onChange={e => setPlanForm({...planForm, title: e.target.value})} required />
                            </div>

                            <div className="form-group">
                                <label>Description</label>
                                <textarea placeholder="Our amazing trip..." value={planForm.description} onChange={e => setPlanForm({...planForm, description: e.target.value})} rows="3"/>
                            </div>

                            <div className="row">
                                <div className="form-group">
                                    <label>Total Budget</label>
                                    <input type="number" value={planForm.budget} onChange={e => setPlanForm({...planForm, budget: e.target.value})} />
                                </div>
                                <div className="form-group">
                                    <label>Currency</label>
                                    <select value={planForm.currency} onChange={e => setPlanForm({...planForm, currency: e.target.value})}>
                                        <option value="USD">USD</option>
                                        <option value="EUR">EUR</option>
                                        <option value="UAH">UAH</option>
                                    </select>
                                </div>
                            </div>

                            <div className="row">
                                <div className="form-group">
                                    <label>Start Date</label>
                                    <input type="date" value={planForm.startDate} onChange={e => setPlanForm({...planForm, startDate: e.target.value})} />
                                </div>
                                <div className="form-group">
                                    <label>End Date</label>
                                    <input type="date" value={planForm.endDate} onChange={e => setPlanForm({...planForm, endDate: e.target.value})} />
                                </div>
                            </div>

                            <div className="form-group">
                                <label className="checkbox-label">
                                    <input type="checkbox" checked={planForm.isPublic} onChange={e => setPlanForm({...planForm, isPublic: e.target.checked})} />
                                    Make this plan Public
                                </label>
                            </div>

                            <div className="modal-actions">
                                <button type="button" onClick={() => setIsPlanModalOpen(false)}>Cancel</button>
                                <button type="submit" className="btn-primary">Save Plan</button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

export default App;